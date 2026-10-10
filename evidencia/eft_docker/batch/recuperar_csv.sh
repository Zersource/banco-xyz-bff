#!/bin/bash
# Espera a que Docker haya reintentado al menos una vez (RestartCount >= 1) y entonces
# publica el CSV de semana_3 en el bind mount (/tmp/batch-datos -> /datos).
cd /Users/zer/banco-xyz-bff
F="-f docker-compose.yaml -f $1/compose.batch-local.yaml -f $1/compose.batch-transitoria.yaml --profile batch"
for i in $(seq 1 600); do
  C=$(docker compose $F ps -a -q batch 2>/dev/null)
  [ -n "$C" ] && rc=$(docker inspect -f '{{.RestartCount}}' "$C" 2>/dev/null) && [ "${rc:-0}" -ge 1 ] && break
  sleep 0.1
done
cp /tmp/batch-src/src/main/resources/data/semana_3/transacciones.csv /tmp/batch-datos/.tmp_transacciones.csv
mv /tmp/batch-datos/.tmp_transacciones.csv /tmp/batch-datos/transacciones.csv
echo "$(date -u +%H:%M:%S.%3N 2>/dev/null || date -u +%H:%M:%S) [script] RestartCount=$rc -> CSV de semana_3 copiado a /tmp/batch-datos/transacciones.csv ($(wc -l < /tmp/batch-datos/transacciones.csv) lineas)"
