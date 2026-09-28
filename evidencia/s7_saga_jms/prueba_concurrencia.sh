#!/bin/bash
# Dispara 4 transferencias simultaneas sobre la MISMA cuenta origen/destino
# (105 -> 106, montos 10/20/30/40) y compara saldo esperado vs saldo real
# despues de que las 4 terminen. Sirve tanto para reproducir la race
# condition (codigo "antes") como para confirmar que quedo resuelta
# (codigo "despues"): el script no cambia, solo el comportamiento del
# codigo que prueba.
#
# Uso: ./prueba_concurrencia.sh [base_url]
# Requiere bff-web arriba (por defecto http://localhost:8081) y curl+python3.

set -u
BASE_URL="${1:-http://localhost:8081}"
ORIGEN=105
DESTINO=106
MONTOS=(10 20 30 40)
TOTAL=100

echo "===== Prueba de concurrencia: $ORIGEN -> $DESTINO, montos ${MONTOS[*]} ====="
date

saldo() {
  curl -s "$BASE_URL/interno/cuentas/$1/saldo"
}

SALDO_ORIGEN_ANTES=$(saldo "$ORIGEN")
SALDO_DESTINO_ANTES=$(saldo "$DESTINO")
echo "Saldo $ORIGEN (origen) antes:  $SALDO_ORIGEN_ANTES"
echo "Saldo $DESTINO (destino) antes: $SALDO_DESTINO_ANTES"

TMPDIR_RUN=$(mktemp -d)
echo "Disparando ${#MONTOS[@]} transferencias en paralelo..."
for m in "${MONTOS[@]}"; do
  curl -s -X POST "$BASE_URL/transferencias" -H "Content-Type: application/json" \
    -d "{\"cuentaOrigenId\":$ORIGEN,\"cuentaDestinoId\":$DESTINO,\"monto\":$m}" \
    > "$TMPDIR_RUN/resp_$m.json" &
done
wait

IDS=()
for m in "${MONTOS[@]}"; do
  id=$(python3 -c "import json;print(json.load(open('$TMPDIR_RUN/resp_$m.json'))['transaccionId'])" 2>/dev/null)
  echo "  monto=$m -> transaccionId=$id"
  IDS+=("$id")
done

echo "Esperando a que las 4 transacciones lleguen a un estado terminal..."
for i in $(seq 1 20); do
  PENDIENTES=0
  for id in "${IDS[@]}"; do
    estado=$(curl -s "$BASE_URL/transferencias/$id" | python3 -c "import sys,json;print(json.load(sys.stdin).get('estado'))" 2>/dev/null)
    if [ "$estado" = "PENDIENTE" ] || [ "$estado" = "None" ] || [ -z "$estado" ]; then
      PENDIENTES=$((PENDIENTES+1))
    fi
  done
  if [ "$PENDIENTES" -eq 0 ]; then break; fi
  sleep 0.5
done

echo "Estados finales:"
for id in "${IDS[@]}"; do
  echo "  id=$id: $(curl -s "$BASE_URL/transferencias/$id")"
done

SALDO_ORIGEN_DESPUES=$(saldo "$ORIGEN")
SALDO_DESTINO_DESPUES=$(saldo "$DESTINO")

ESPERADO_ORIGEN=$(python3 -c "print($SALDO_ORIGEN_ANTES - $TOTAL)")
ESPERADO_DESTINO=$(python3 -c "print($SALDO_DESTINO_ANTES + $TOTAL)")

echo
echo "Cuenta $ORIGEN (origen):  antes=$SALDO_ORIGEN_ANTES  esperado=$ESPERADO_ORIGEN  real=$SALDO_ORIGEN_DESPUES"
echo "Cuenta $DESTINO (destino): antes=$SALDO_DESTINO_ANTES  esperado=$ESPERADO_DESTINO  real=$SALDO_DESTINO_DESPUES"

if [ "$SALDO_ORIGEN_DESPUES" = "$ESPERADO_ORIGEN" ] && [ "$SALDO_DESTINO_DESPUES" = "$ESPERADO_DESTINO" ]; then
  echo "RESULTADO: OK (saldo real = saldo esperado en ambas cuentas)"
else
  echo "RESULTADO: DESCUADRE (saldo real != saldo esperado)"
fi

rm -rf "$TMPDIR_RUN"
echo "===== fin de la corrida ====="
echo
