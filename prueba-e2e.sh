#!/usr/bin/env bash
# Prueba end-to-end del ecosistema (EFT). Requiere el stack levantado (ver README):
# eureka-server, config-server, auth-server, cuentas, clientes y los 3 BFF.
# Sale con codigo distinto de 0 si alguna verificacion falla.
#
# Uso: ./prueba-e2e.sh            (todas las pruebas)
#      ./prueba-e2e.sh seccion    (solo una: registro, seguridad, retiro, payload, resiliencia)
cd "$(dirname "$0")" || exit 2

AUTH=http://localhost:9000
TOTAL=0
FALLAS=0
TMP=$(mktemp -d)
CUERPO="$TMP/cuerpo.json"

# ---------- utilidades ----------

chequear() { # descripcion esperado real
    TOTAL=$((TOTAL + 1))
    if [ "$2" == "$3" ]; then
        echo "  OK    $1 (esperado=$2)"
    else
        echo "  FALLA $1 (esperado=$2, real=$3)"
        FALLAS=$((FALLAS + 1))
    fi
}

token() { # client_id secret scope
    curl -s -u "$1:$2" -d grant_type=client_credentials -d scope="$3" "$AUTH/oauth2/token" \
        | python3 -c 'import json,sys; print(json.load(sys.stdin).get("access_token",""))'
}

# http_code url [token] [metodo] [json]  -> imprime el codigo y deja el cuerpo en $CUERPO
http_code() {
    local url=$1 tok=${2:-} metodo=${3:-GET} datos=${4:-}
    local args=(-s -o "$CUERPO" -w '%{http_code}' -X "$metodo")
    [ -n "$tok" ] && args+=(-H "Authorization: Bearer $tok")
    [ -n "$datos" ] && args+=(-H 'Content-Type: application/json' -d "$datos")
    curl "${args[@]}" "$url"
}

json() { # expresion python sobre el objeto "d" del cuerpo
    python3 -c "import json,sys; d=json.load(open('$CUERPO')); print($1)"
}

esperar_eureka() { # NOMBRE segundos
    for _ in $(seq 1 "$2"); do
        curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps/"$1" | grep -q '"status":"UP"' && return 0
        sleep 1
    done
    return 1
}

reiniciar() { # modulo
    nohup java -Xmx256m -jar "$1/target/$1-1.0.0.jar" > "$TMP/$1.log" 2>&1 &
}

esperar_codigo() { # url token esperado segundos
    local real=""
    for _ in $(seq 1 "$4"); do
        real=$(http_code "$1" "$2")
        [ "$real" == "$3" ] && { echo "$real"; return 0; }
        sleep 2
    done
    echo "$real"
}

# ---------- secciones ----------

registro() {
    echo "== Registro en Eureka"
    for app in CUENTAS CLIENTES BFF-WEB BFF-MOVIL BFF-CAJERO; do
        estado=$(curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps/$app \
            | python3 -c 'import json,sys; print(json.load(sys.stdin)["application"]["instance"][0]["status"])' 2>/dev/null)
        chequear "$app registrado en Eureka" UP "${estado:-NO_REGISTRADO}"
    done
}

seguridad() {
    echo "== Seguridad por canal (200 token propio, 401 sin token, 403 token de otro canal)"
    local urls=("http://localhost:8081/api/web/cuentas/101" "http://localhost:8082/api/movil/cuentas/101" "http://localhost:8083/api/cajero/cuentas/101/saldo")
    local nombres=(bff-web bff-movil bff-cajero)
    local propios=("$TOKEN_WEB" "$TOKEN_MOVIL" "$TOKEN_CAJERO")
    local ajenos=("$TOKEN_MOVIL" "$TOKEN_CAJERO" "$TOKEN_WEB")
    for i in 0 1 2; do
        chequear "${nombres[$i]} con token propio" 200 "$(http_code "${urls[$i]}" "${propios[$i]}")"
        chequear "${nombres[$i]} sin token" 401 "$(http_code "${urls[$i]}")"
        chequear "${nombres[$i]} con token de otro canal" 403 "$(http_code "${urls[$i]}" "${ajenos[$i]}")"
    done
    echo "  -- contenido"
    http_code http://localhost:8081/api/web/cuentas/101 "$TOKEN_WEB" > /dev/null
    local nombre saldo_web
    nombre=$(json "d['nombre']")
    saldo_web=$(json "d['saldo']")
    chequear "web trae el nombre que entrega clientes" "John Doe" "$nombre"
    http_code http://localhost:8084/cuentas/101/saldo "$TOKEN_WEB" > /dev/null
    chequear "web trae el saldo que entrega cuentas" "$(cat "$CUERPO")" "$saldo_web"
    http_code http://localhost:8081/api/web/cuentas "$TOKEN_WEB" > /dev/null
    chequear "web lista las cuentas con nombre" 8 "$(json "len([c for c in d if c['nombre']])")"
    http_code http://localhost:8081/api/web/transacciones "$TOKEN_WEB" > /dev/null
    chequear "web lista las transacciones" 10 "$(json "len(d)")"
    echo "  -- acceso directo a los servicios de datos"
    chequear "cuentas sin token" 401 "$(http_code http://localhost:8084/cuentas/101)"
    chequear "clientes sin token" 401 "$(http_code http://localhost:8085/clientes/101)"
    chequear "cuentas con token de canal" 200 "$(http_code http://localhost:8084/cuentas/101 "$TOKEN_MOVIL")"
    chequear "clientes con token de canal" 200 "$(http_code http://localhost:8085/clientes/101 "$TOKEN_MOVIL")"
    chequear "clientes cuenta inexistente" 404 "$(http_code http://localhost:8085/clientes/9999 "$TOKEN_MOVIL")"
}

retiro() {
    echo "== Retiro del cajero (pasa por debitar() atomico de cuentas)"
    http_code http://localhost:8083/api/cajero/cuentas/101/saldo "$TOKEN_CAJERO" > /dev/null
    local antes
    antes=$(json "d['saldo']")
    chequear "retiro de 100 (200)" 200 "$(http_code http://localhost:8083/api/cajero/cuentas/101/retiro "$TOKEN_CAJERO" POST '{"monto":100}')"
    chequear "saldo resultante = saldo anterior - 100" "$(python3 -c "print($antes - 100)")" "$(json "d['saldoResultante']")"
    chequear "retiro mayor al saldo (400)" 400 "$(http_code http://localhost:8083/api/cajero/cuentas/101/retiro "$TOKEN_CAJERO" POST '{"monto":99999999}')"
    chequear "retiro de cuenta inexistente (404)" 404 "$(http_code http://localhost:8083/api/cajero/cuentas/9999/retiro "$TOKEN_CAJERO" POST '{"monto":10}')"
    chequear "retiro con monto negativo (400)" 400 "$(http_code http://localhost:8083/api/cajero/cuentas/101/retiro "$TOKEN_CAJERO" POST '{"monto":-5}')"
    http_code http://localhost:8083/api/cajero/cuentas/101/saldo "$TOKEN_CAJERO" > /dev/null
    chequear "saldo final = saldo anterior - 100" "$(python3 -c "print($antes - 100)")" "$(json "d['saldo']")"
    echo "  -- 20 retiros simultaneos de 1"
    http_code http://localhost:8083/api/cajero/cuentas/101/saldo "$TOKEN_CAJERO" > /dev/null
    local base
    base=$(json "d['saldo']")
    for _ in $(seq 1 20); do
        curl -s -o /dev/null -X POST -H "Authorization: Bearer $TOKEN_CAJERO" -H 'Content-Type: application/json' \
            -d '{"monto":1}' http://localhost:8083/api/cajero/cuentas/101/retiro &
    done
    wait
    http_code http://localhost:8083/api/cajero/cuentas/101/saldo "$TOKEN_CAJERO" > /dev/null
    chequear "saldo exacto tras 20 retiros simultaneos" "$(python3 -c "print($base - 20)")" "$(json "d['saldo']")"
}

payload() {
    echo "== Peso de la respuesta por canal (cuenta 101)"
    local w m c
    w=$(curl -s -o /dev/null -w '%{size_download}' -H "Authorization: Bearer $TOKEN_WEB" http://localhost:8081/api/web/cuentas/101)
    m=$(curl -s -o /dev/null -w '%{size_download}' -H "Authorization: Bearer $TOKEN_MOVIL" http://localhost:8082/api/movil/cuentas/101)
    c=$(curl -s -o /dev/null -w '%{size_download}' -H "Authorization: Bearer $TOKEN_CAJERO" http://localhost:8083/api/cajero/cuentas/101/saldo)
    echo "  bytes: web=$w movil=$m cajero=$c"
    chequear "movil pesa menos que web" true "$([ "$m" -lt "$w" ] && echo true || echo false)"
    chequear "cajero pesa menos que movil" true "$([ "$c" -lt "$m" ] && echo true || echo false)"
    http_code http://localhost:8082/api/movil/cuentas/101 "$TOKEN_MOVIL" > /dev/null
    chequear "movil trae 3 transacciones (id, monto, tipo)" 3 "$(json "len(d['ultimasTransacciones'])")"
}

resiliencia() {
    echo "== Circuit Breaker + fallback con cuentas apagado"
    pkill -f 'cuentas-1.0.0.jar'
    sleep 2
    for _ in 1 2 3 4 5; do http_code http://localhost:8082/api/movil/cuentas/101 "$TOKEN_MOVIL" > /dev/null; done
    chequear "bff-web con cuentas caido" 503 "$(http_code http://localhost:8081/api/web/cuentas/101 "$TOKEN_WEB")"
    chequear "bff-movil con cuentas caido" 503 "$(http_code http://localhost:8082/api/movil/cuentas/101 "$TOKEN_MOVIL")"
    chequear "bff-movil mensaje del fallback" "El servicio cuentas no esta disponible en este momento" "$(json "d['mensaje']")"
    chequear "bff-cajero saldo con cuentas caido" 503 "$(http_code http://localhost:8083/api/cajero/cuentas/101/saldo "$TOKEN_CAJERO")"
    chequear "bff-cajero retiro con cuentas caido" 503 "$(http_code http://localhost:8083/api/cajero/cuentas/101/retiro "$TOKEN_CAJERO" POST '{"monto":10}')"
    echo "  -- recuperacion (reinicio de cuentas)"
    reiniciar cuentas
    chequear "bff-movil se recupera solo" 200 "$(esperar_codigo http://localhost:8082/api/movil/cuentas/101 "$TOKEN_MOVIL" 200 60)"

    echo "== Fallback degradado con clientes apagado"
    pkill -f 'clientes-1.0.0.jar'
    sleep 2
    chequear "bff-web responde 200 sin clientes" 200 "$(http_code http://localhost:8081/api/web/cuentas/101 "$TOKEN_WEB")"
    chequear "bff-web sin nombre (fallback)" None "$(json "d['nombre']")"
    chequear "bff-movil responde 200 sin clientes" 200 "$(http_code http://localhost:8082/api/movil/cuentas/101 "$TOKEN_MOVIL")"
    reiniciar clientes
    chequear "bff-web recupera el nombre" "John Doe" "$(
        for _ in $(seq 1 30); do
            http_code http://localhost:8081/api/web/cuentas/101 "$TOKEN_WEB" > /dev/null
            n=$(json "d['nombre']")
            [ "$n" != None ] && break
            sleep 2
        done
        echo "$n")"
}

# ---------- ejecucion ----------

TOKEN_WEB=$(token cliente-web WEB-KEY-2024 web)
TOKEN_MOVIL=$(token cliente-movil MOVIL-KEY-2024 movil)
TOKEN_CAJERO=$(token cliente-cajero CAJERO-KEY-2024 cajero)
if [ -z "$TOKEN_WEB" ] || [ -z "$TOKEN_MOVIL" ] || [ -z "$TOKEN_CAJERO" ]; then
    echo "No se pudieron obtener los tokens del auth-server (localhost:9000). Levanta el stack primero."
    exit 2
fi

SECCION=${1:-todo}
if [ "$SECCION" == todo ]; then
    registro; seguridad; retiro; payload; resiliencia
else
    "$SECCION"
fi

echo
echo "RESUMEN: $((TOTAL - FALLAS))/$TOTAL verificaciones OK"
rm -rf "$TMP"
[ "$FALLAS" -eq 0 ]
