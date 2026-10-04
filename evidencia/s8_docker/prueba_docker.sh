#!/bin/bash
# Prueba del stack (Exp3 S8): OAuth2 con auth-server + saga + circuit breaker.
# Usa los puertos publicados en localhost. Requiere el stack arriba, curl y python3.
#
# Uso:
#   ./prueba_docker.sh              -> corre las dos secciones
#   ./prueba_docker.sh funcional    -> solo OAuth2 (tokens, 401/403) y saga
#   ./prueba_docker.sh resiliencia  -> solo circuit breaker
#
# Ejecutar desde la raiz del repo (donde esta docker-compose.yaml).

set -u
WEB=http://localhost:8081
MOVIL=http://localhost:8082
CAJERO=http://localhost:8083
AUTH=http://localhost:9000
SECCION="${1:-todo}"

# Pide un access_token al auth-server: token <canal> <secreto> [sin_scope]
token() {
  if [ "${3:-}" = "sin_scope" ]; then
    curl -s -u "cliente-$1:$2" -d grant_type=client_credentials "$AUTH/oauth2/token"
  else
    curl -s -u "cliente-$1:$2" -d grant_type=client_credentials -d "scope=$1" "$AUTH/oauth2/token"
  fi | python3 -c "import sys,json;print(json.load(sys.stdin).get('access_token',''))"
}

# Devuelve solo el codigo HTTP de una llamada GET con token opcional
codigo_get() {
  if [ -n "${2:-}" ]; then
    curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $2" "$1"
  else
    curl -s -o /dev/null -w "%{http_code}" "$1"
  fi
}

# Codigo HTTP de un POST con cuerpo JSON y token opcional: codigo_post <url> <json> [token]
codigo_post() {
  if [ -n "${3:-}" ]; then
    curl -s -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $3" -d "$2" "$1"
  else
    curl -s -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" -d "$2" "$1"
  fi
}

# GET con el token del canal web (para saldos y estado de transferencias)
get_web() {
  curl -s -H "Authorization: Bearer $TOKEN_WEB" "$1"
}

# Espera a que una transferencia salga de PENDIENTE/DEBITO_OK
esperar_estado() {
  for i in $(seq 1 20); do
    est=$(get_web "$WEB/transferencias/$1" | python3 -c "import sys,json;print(json.load(sys.stdin).get('estado'))" 2>/dev/null)
    if [ "$est" != "PENDIENTE" ] && [ "$est" != "DEBITO_OK" ] && [ -n "$est" ] && [ "$est" != "None" ]; then
      break
    fi
    sleep 0.5
  done
  echo "$est"
}

funcional() {
  echo
  echo "################ SECCION FUNCIONAL ################"
  date

  echo "== 0. Servicios registrados en Eureka"
  curl -s http://localhost:8761/eureka/apps | grep -E "<name>|<status>"

  echo "== 1. Access token de cada canal (auth-server, client_credentials)"
  TOKEN_WEB=$(token web WEB-KEY-2024)
  TOKEN_MOVIL=$(token movil MOVIL-KEY-2024)
  TOKEN_CAJERO=$(token cajero CAJERO-KEY-2024)
  echo "token web:    ${TOKEN_WEB:0:30}..."
  echo "token movil:  ${TOKEN_MOVIL:0:30}..."
  echo "token cajero: ${TOKEN_CAJERO:0:30}..."

  echo "== 2. Endpoint de cada canal con su token (esperado 200)"
  echo "web    /api/web/cuentas              -> $(codigo_get $WEB/api/web/cuentas "$TOKEN_WEB")"
  echo "movil  /api/movil/cuentas/101        -> $(codigo_get $MOVIL/api/movil/cuentas/101 "$TOKEN_MOVIL")"
  echo "cajero /api/cajero/cuentas/101/saldo -> $(codigo_get $CAJERO/api/cajero/cuentas/101/saldo "$TOKEN_CAJERO")"

  echo "== 3. Sin token (esperado 401)"
  echo "web    /api/web/cuentas                -> $(codigo_get $WEB/api/web/cuentas)"
  echo "movil  /api/movil/cuentas/101          -> $(codigo_get $MOVIL/api/movil/cuentas/101)"
  echo "cajero /api/cajero/cuentas/101/saldo   -> $(codigo_get $CAJERO/api/cajero/cuentas/101/saldo)"
  echo "web    /interno/cuentas/101/saldo      -> $(codigo_get $WEB/interno/cuentas/101/saldo)"
  echo "web    POST /transferencias            -> $(codigo_post $WEB/transferencias '{"cuentaOrigenId":101,"cuentaDestinoId":102,"monto":10}')"

  echo "== 4. Token de otro canal (esperado 403)"
  echo "token movil en bff-web    -> $(codigo_get $WEB/api/web/cuentas "$TOKEN_MOVIL")"
  echo "token cajero en bff-movil -> $(codigo_get $MOVIL/api/movil/cuentas/101 "$TOKEN_CAJERO")"
  echo "token web en bff-cajero   -> $(codigo_get $CAJERO/api/cajero/cuentas/101/saldo "$TOKEN_WEB")"

  echo "== 4b. Token SIN scope (cliente-movil sin parametro scope) en bff-movil (esperado 403)"
  TOKEN_SIN_SCOPE=$(token movil MOVIL-KEY-2024 sin_scope)
  echo "movil /api/movil/cuentas/101 -> $(codigo_get $MOVIL/api/movil/cuentas/101 "$TOKEN_SIN_SCOPE")"

  echo "== 4c. Token basura (Bearer abc) (esperado 401)"
  echo "movil /api/movil/cuentas/101 -> $(codigo_get $MOVIL/api/movil/cuentas/101 abc)"

  echo "== 5. Saga camino feliz: 101 -> 102, monto 10 (esperado COMPLETADA)"
  echo "saldo 101 antes:   $(get_web $WEB/interno/cuentas/101/saldo)"
  echo "saldo 102 antes:   $(get_web $WEB/interno/cuentas/102/saldo)"
  RESP=$(curl -s -X POST "$WEB/transferencias" -H "Authorization: Bearer $TOKEN_WEB" -H "Content-Type: application/json" \
    -d '{"cuentaOrigenId":101,"cuentaDestinoId":102,"monto":10}')
  echo "respuesta inicial: $RESP"
  ID=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['transaccionId'])")
  echo "estado final:      $(esperar_estado "$ID")"
  echo "saldo 101 despues: $(get_web $WEB/interno/cuentas/101/saldo)"
  echo "saldo 102 despues: $(get_web $WEB/interno/cuentas/102/saldo)"

  echo "== 6. Saga compensacion: 101 -> 999 (destino inexistente), monto 10 (esperado REVERTIDA)"
  echo "saldo 101 antes:   $(get_web $WEB/interno/cuentas/101/saldo)"
  RESP=$(curl -s -X POST "$WEB/transferencias" -H "Authorization: Bearer $TOKEN_WEB" -H "Content-Type: application/json" \
    -d '{"cuentaOrigenId":101,"cuentaDestinoId":999,"monto":10}')
  echo "respuesta inicial: $RESP"
  ID=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['transaccionId'])")
  echo "estado final:      $(esperar_estado "$ID")"
  echo "saldo 101 despues: $(get_web $WEB/interno/cuentas/101/saldo)  (debe ser igual al de antes)"
}

resiliencia() {
  echo
  echo "################ SECCION RESILIENCIA ################"
  date

  echo "== 1. Estado inicial: bff-movil responde con bff-web arriba"
  TOKEN_MOVIL=$(token movil MOVIL-KEY-2024)
  echo "movil /api/movil/cuentas/101 -> $(codigo_get $MOVIL/api/movil/cuentas/101 "$TOKEN_MOVIL")"

  echo "== 2. Se baja bff-web (docker compose stop bff-web)"
  docker compose stop bff-web

  echo "== 3. Llamadas a bff-movil hasta que el circuito se abra (esperado 503 con fallback)"
  for i in $(seq 1 8); do
    echo "llamada $i -> $(codigo_get $MOVIL/api/movil/cuentas/101 "$TOKEN_MOVIL")"
  done
  echo "-- cuerpo de la respuesta con el circuito abierto:"
  curl -s -H "Authorization: Bearer $TOKEN_MOVIL" "$MOVIL/api/movil/cuentas/101"
  echo
  echo "-- logs de bff-movil sobre el circuito (transiciones y llamadas rechazadas):"
  docker compose logs bff-movil 2>&1 | grep -E "STATE_TRANSITION|NOT_PERMITTED" | tail -8

  echo "== 4. Se levanta bff-web (docker compose start bff-web); puede volver con otra IP"
  docker compose start bff-web

  echo "== 5. Se reintenta bff-movil hasta ~90 s (cache de Eureka + HALF_OPEN -> CLOSED)"
  RECUPERADO=no
  for i in $(seq 1 18); do
    sleep 5
    COD=$(codigo_get $MOVIL/api/movil/cuentas/101 "$TOKEN_MOVIL")
    echo "intento $i (t=$((i*5))s) -> $COD"
    if [ "$COD" = "200" ]; then
      RECUPERADO=si
      # 3 llamadas mas para completar la ventana HALF_OPEN (3 llamadas permitidas)
      # y que el circuito llegue a CLOSED
      for j in 1 2 3; do
        sleep 1
        echo "llamada de cierre $j -> $(codigo_get $MOVIL/api/movil/cuentas/101 "$TOKEN_MOVIL")"
      done
      break
    fi
  done
  echo "-- bff-web registrado de nuevo en Eureka:"
  curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps/BFF-WEB \
    | python3 -c "import sys,json;[print(i['app'],i['status'],i['ipAddr']) for i in json.load(sys.stdin)['application']['instance']]"
  echo "-- logs de bff-movil sobre el circuito (transiciones):"
  docker compose logs bff-movil 2>&1 | grep "STATE_TRANSITION" | tail -8
  echo "Recuperado (200 de nuevo): $RECUPERADO"
}

case "$SECCION" in
  funcional)   funcional ;;
  resiliencia) resiliencia ;;
  todo)        funcional; resiliencia ;;
  *) echo "Uso: $0 [funcional|resiliencia]"; exit 1 ;;
esac

echo
echo "===== fin de la prueba ====="
