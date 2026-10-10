#!/usr/bin/env bash
# Prueba de falla TRANSITORIA con recuperacion automatica del batch (restart: on-failure:3).
# 1) crea una carpeta temporal vacia (mktemp -d) y la monta en /datos (compose.falla-transitoria.yaml);
# 2) lanza `docker compose ... up batch` en segundo plano: el Job falla porque /datos/transacciones.csv
#    no existe;
# 3) cuando Docker ya reintento (RestartCount >= 1) copia el CSV de semana_3 del repo a esa carpeta;
# 4) un intento posterior termina con exit 0. Imprime "ExitCode RestartCount" (esperado: 0 1 o mayor).
# Uso, desde cualquier carpeta:  docker/batch/test/recuperar_csv.sh
# Compatible con bash 3.2 (macOS) y Linux. Requiere docker compose v2.
set -u
cd "$(dirname "$0")/../../.." || exit 2          # raiz del repo
CSV=batch/src/main/resources/data/semana_3/transacciones.csv
[ -f "$CSV" ] || { echo "no existe $CSV" >&2; exit 2; }

BATCH_DATOS_DIR=$(mktemp -d) || exit 2
export BATCH_DATOS_DIR
chmod 755 "$BATCH_DATOS_DIR"                     # el contenedor corre como usuario no root
trap 'rm -rf "$BATCH_DATOS_DIR"' EXIT
DC="docker compose -f docker-compose.yaml -f docker/batch/test/compose.falla-transitoria.yaml --profile batch"

$DC rm -fsv batch > /dev/null 2>&1
echo "[script] carpeta de datos: $BATCH_DATOS_DIR (vacia)"
$DC up batch &
UP_PID=$!

rc=0
for _ in $(seq 1 600); do                        # hasta ~60 s
    c=$($DC ps -aq batch 2> /dev/null)
    if [ -n "$c" ]; then
        rc=$(docker inspect -f '{{.RestartCount}}' "$c" 2> /dev/null || echo 0)
        [ "${rc:-0}" -ge 1 ] && break
    fi
    sleep 0.1
done
cp "$CSV" "$BATCH_DATOS_DIR/.tmp_transacciones.csv"
chmod 644 "$BATCH_DATOS_DIR/.tmp_transacciones.csv"
mv "$BATCH_DATOS_DIR/.tmp_transacciones.csv" "$BATCH_DATOS_DIR/transacciones.csv"   # aparece de una vez
echo "[script] $(date -u +%H:%M:%S) RestartCount=$rc -> CSV de semana_3 copiado ($(wc -l < "$CSV" | tr -d ' ') lineas)"

wait "$UP_PID"
c=$($DC ps -aq batch)
echo "[script] ExitCode RestartCount: $(docker inspect -f '{{.State.ExitCode}} {{.RestartCount}}' "$c")"
