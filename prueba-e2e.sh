#!/usr/bin/env bash
# Prueba end-to-end del ecosistema (EFT). Requiere el stack levantado (ver README):
# eureka-server, config-server, auth-server, cuentas, clientes y los 3 BFF.
# Sale con codigo distinto de 0 si alguna verificacion falla.
#
# Modos (variable MODO):
#   local  (por defecto) los servicios son procesos java -jar en esta maquina y Kafka escucha en
#          localhost:9092; apagar/encender un servicio es pkill / java -jar.
#   docker los servicios corren con docker compose (desde la raiz del repo); apagar/encender es
#          docker compose stop/start/restart <servicio>, y Kafka se consulta con
#          docker compose exec kafka ...
# Los logs se leen de $LOGS_DIR/<servicio>.log (local; opcional: si no esta definido se omiten esas
# comprobaciones y el resumen lo dice) o de `docker compose logs --no-log-prefix <servicio>` (docker).
# En modo local, para consultar los topics se necesita kafka-topics.sh en el PATH o KAFKA_HOME.
#
# Uso: [MODO=docker] [LOGS_DIR=ruta] ./prueba-e2e.sh            (todas las pruebas)
#      ./prueba-e2e.sh seccion    (solo una: registro, seguridad, retiro, payload, salud,
#                                  saga, reinicio_pagos, resiliencia)
cd "$(dirname "$0")" || exit 2

AUTH=http://localhost:9000
MODO=${MODO:-local}
LOGS_DIR=${LOGS_DIR:-}
OMITIDAS=0
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

parar_servicio() { # servicio
    if [ "$MODO" == docker ]; then
        docker compose stop "$1" > /dev/null 2>&1
    else
        pkill -f "$1-1.0.0.jar"
    fi
}

iniciar_servicio() { # servicio (en local, si hay LOGS_DIR sigue escribiendo en el mismo log)
    if [ "$MODO" == docker ]; then
        docker compose start "$1" > /dev/null 2>&1
    else
        local log="$TMP/$1.log"
        [ -n "$LOGS_DIR" ] && [ -d "$LOGS_DIR" ] && log="$LOGS_DIR/$1.log"
        nohup java -Xmx256m -jar "$1/target/$1-1.0.0.jar" >> "$log" 2>&1 &
    fi
}

reiniciar_servicio() { # servicio: reinicio de ese solo servicio
    if [ "$MODO" == docker ]; then
        docker compose restart "$1" > /dev/null 2>&1
    else
        parar_servicio "$1"
        sleep 2
        iniciar_servicio "$1"
    fi
}

# log_disponible servicio -> 0 si se puede leer su log en este modo
log_disponible() {
    [ "$MODO" == docker ] || { [ -n "$LOGS_DIR" ] && [ -f "$LOGS_DIR/$1.log" ]; }
}

leer_log() { # servicio -> imprime todo su log
    if [ "$MODO" == docker ]; then
        docker compose logs --no-log-prefix "$1" 2>&1
    else
        cat "$LOGS_DIR/$1.log"
    fi
}

lineas_log() { # servicio -> cantidad de lineas del log hasta ahora
    leer_log "$1" | wc -l | tr -d ' '
}

lineas_nuevas() { # servicio desde -> lineas del log posteriores a esa cantidad
    leer_log "$1" | tail -n +$(($2 + 1))
}

kafka_listo() {
    if [ "$MODO" == docker ]; then
        docker compose exec -T kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1
    else
        nc -z localhost 9092 2> /dev/null
    fi
}

# kafka_topics_describe -> salida de kafka-topics --describe (vacia si no hay forma de consultarlo)
kafka_topics_describe() {
    if [ "$MODO" == docker ]; then
        docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe 2> /dev/null
    else
        local bin
        bin=$(command -v kafka-topics.sh || command -v kafka-topics || ls "${KAFKA_HOME:-/nonexistente}/bin/kafka-topics.sh" 2> /dev/null)
        [ -n "$bin" ] && "$bin" --bootstrap-server localhost:9092 --describe 2> /dev/null
    fi
}

es_uuid() { # texto -> "true" si tiene formato UUID
    [[ "$1" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]] && echo true || echo false
}

saldo() { # cuentaId -> saldo segun cuentas
    curl -s -H "Authorization: Bearer $TOKEN_WEB" "http://localhost:8084/cuentas/$1/saldo"
}

# transferir origen destino monto -> imprime el transaccionId (cuerpo de la respuesta en $CUERPO)
transferir() {
    http_code http://localhost:8081/transferencias "$TOKEN_WEB" POST \
        "{\"cuentaOrigenId\":$1,\"cuentaDestinoId\":$2,\"monto\":$3}" > /dev/null
    json "d.get('transaccionId')"
}

# estado_de id -> estado actual segun pagos (via bff-web)
estado_de() {
    http_code "http://localhost:8081/transferencias/$1" "$TOKEN_WEB" > /dev/null
    json "d.get('estado')"
}

esperar_estado() { # id estado segundos -> imprime el ultimo estado visto
    local e=""
    for _ in $(seq 1 "$3"); do
        e=$(estado_de "$1")
        [ "$e" == "$2" ] && break
        sleep 1
    done
    echo "$e"
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

# Deja cerrados los Circuit Breaker de los 3 BFF: exige 3 respuestas 200 seguidas de cada
# canal. La ventana del breaker es de 5 llamadas y conserva las fallas de corridas
# anteriores, asi que sin esto una corrida puede heredar un circuito abierto de la otra.
cerrar_circuitos() { # segundos
    local urls=("http://localhost:8081/api/web/cuentas/101" "http://localhost:8082/api/movil/cuentas/101" "http://localhost:8083/api/cajero/cuentas/101/saldo")
    local toks=("$TOKEN_WEB" "$TOKEN_MOVIL" "$TOKEN_CAJERO")
    local seguidas intentos
    for i in 0 1 2; do
        seguidas=0
        intentos=0
        while [ "$seguidas" -lt 3 ] && [ "$intentos" -lt "$1" ]; do
            if [ "$(http_code "${urls[$i]}" "${toks[$i]}")" == 200 ]; then seguidas=$((seguidas + 1)); else seguidas=0; sleep 2; fi
            intentos=$((intentos + 1))
        done
    done
}

# Hace 3 transferencias reales de 1 (101 -> 102) seguidas con 202: asegura que bff-web ve a pagos
# (Eureka y el balanceador pueden tardar ~1 min en mostrar una instancia recien reiniciada) y cierra
# su Circuit Breaker. Un 404 no sirve: el breaker lo ignora y no cuenta como llamada exitosa.
# Imprime cuantas exitosas seguidas logro (3 si todo bien).
calentar_pagos() { # intentos
    local exitosas=0 intentos=0
    while [ "$exitosas" -lt 3 ] && [ "$intentos" -lt "$1" ]; do
        if [ "$(http_code http://localhost:8081/transferencias "$TOKEN_WEB" POST '{"cuentaOrigenId":101,"cuentaDestinoId":102,"monto":1}')" == 202 ]; then
            exitosas=$((exitosas + 1))
        else
            exitosas=0
            sleep 2
        fi
        intentos=$((intentos + 1))
    done
    [ "$exitosas" -gt 0 ] && sleep 3  # deja terminar la saga de esas transferencias antes de medir saldos
    echo "$exitosas"
}

# ---------- secciones ----------

registro() {
    echo "== Registro en Eureka"
    for app in CUENTAS CLIENTES PAGOS BFF-WEB BFF-MOVIL BFF-CAJERO; do
        # Eureka cachea su respuesta hasta ~30 s: se espera a que un reinicio reciente aparezca
        esperar_eureka "$app" 45
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
    echo "  -- /transferencias de bff-web exige scope web"
    chequear "POST /transferencias con token movil" 403 "$(http_code http://localhost:8081/transferencias "$TOKEN_MOVIL" POST '{"cuentaOrigenId":101,"cuentaDestinoId":102,"monto":1}')"
    chequear "POST /transferencias con token cajero" 403 "$(http_code http://localhost:8081/transferencias "$TOKEN_CAJERO" POST '{"cuentaOrigenId":101,"cuentaDestinoId":102,"monto":1}')"
    chequear "GET /transferencias/{id} con token movil" 403 "$(http_code http://localhost:8081/transferencias/00000000-0000-0000-0000-000000000000 "$TOKEN_MOVIL")"
    chequear "GET /transferencias/{id} con token cajero" 403 "$(http_code http://localhost:8081/transferencias/00000000-0000-0000-0000-000000000000 "$TOKEN_CAJERO")"
    chequear "POST /transferencias sin token" 401 "$(http_code http://localhost:8081/transferencias "" POST '{"cuentaOrigenId":101,"cuentaDestinoId":102,"monto":1}')"
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

salud() {
    echo "== /actuator/health sin token (healthcheck de las imagenes)"
    for par in cuentas:8084 clientes:8085 pagos:8086; do
        chequear "${par%%:*} /actuator/health sin token" 200 "$(http_code "http://localhost:${par##*:}/actuator/health")"
        chequear "${par%%:*} health status" UP "$(json "d['status']")"
    done
}

saga() {
    echo "== Saga de transferencias sobre Kafka (bff-web -> pagos -> cuentas)"
    if ! kafka_listo; then
        chequear "Kafka disponible" true false
        return
    fi
    local desc
    desc=$(kafka_topics_describe)
    if [ -z "$desc" ]; then
        echo "  OMITIDO topics (no hay forma de ejecutar kafka-topics en este modo/maquina)"
        OMITIDAS=$((OMITIDAS + 1))
    else
        chequear "6 topics de la saga con 3 particiones y replicacion 1" 6 \
            "$(grep -E '^Topic: transferencia\.[a-z-]+[[:space:]].*PartitionCount: 3[[:space:]]+ReplicationFactor: 1' <<< "$desc" | wc -l | tr -d ' ')"
    fi
    chequear "bff-web alcanza a pagos (calentamiento)" 3 "$(calentar_pagos 60)"
    chequear "pagos sin token" 401 "$(http_code http://localhost:8086/transferencias/1)"
    chequear "pagos con token de otro canal" 403 "$(http_code http://localhost:8086/transferencias/1 "$TOKEN_MOVIL")"

    # Los logs acumulan corridas anteriores (los ids se repiten si se reinicia pagos): solo se
    # revisa lo que se escribe a partir de aqui.
    local ini_pagos=0 ini_cuentas=0 ini_clientes=0 hay_logs=true
    for f in pagos cuentas clientes; do log_disponible "$f" || hay_logs=false; done
    if $hay_logs; then
        ini_pagos=$(lineas_log pagos)
        ini_cuentas=$(lineas_log cuentas)
        ini_clientes=$(lineas_log clientes)
    fi

    echo "  -- exito"
    local o_antes d_antes id
    o_antes=$(saldo 101); d_antes=$(saldo 102)
    chequear "POST /transferencias responde 202" 202 "$(http_code http://localhost:8081/transferencias "$TOKEN_WEB" POST '{"cuentaOrigenId":101,"cuentaDestinoId":102,"monto":500}')"
    id=$(json "d.get('transaccionId')")
    chequear "estado final" COMPLETADA "$(esperar_estado "$id" COMPLETADA 30)"
    sleep 1
    chequear "saldo origen -500" "$(python3 -c "print($o_antes - 500)")" "$(saldo 101)"
    chequear "saldo destino +500" "$(python3 -c "print($d_antes + 500)")" "$(saldo 102)"
    ID_EXITO=$id
    chequear "transaccionId tiene formato UUID" true "$(es_uuid "$id")"

    echo "  -- compensacion (destino inexistente)"
    o_antes=$(saldo 101)
    id=$(transferir 101 9999 300)
    chequear "estado final" REVERTIDA "$(esperar_estado "$id" REVERTIDA 30)"
    chequear "mensaje de la compensacion" "Error al acreditar cuenta destino: No se encontro la cuenta con id: 9999" "$(json "d.get('mensaje')")"
    chequear "saldo origen restituido" "$o_antes" "$(saldo 101)"
    ID_COMPENSADA=$id

    echo "  -- debito fallido por fondos"
    o_antes=$(saldo 101); d_antes=$(saldo 102)
    id=$(transferir 101 102 99999999)
    chequear "estado final" FALLIDA "$(esperar_estado "$id" FALLIDA 30)"
    chequear "mensaje" "Fondos insuficientes en cuenta origen" "$(json "d.get('mensaje')")"
    chequear "saldo origen sin cambios" "$o_antes" "$(saldo 101)"
    chequear "saldo destino sin cambios" "$d_antes" "$(saldo 102)"

    echo "  -- validaciones y 404 (reenviados desde pagos)"
    chequear "origen igual a destino (400)" 400 "$(http_code http://localhost:8081/transferencias "$TOKEN_WEB" POST '{"cuentaOrigenId":101,"cuentaDestinoId":101,"monto":5}')"
    chequear "monto negativo (400)" 400 "$(http_code http://localhost:8081/transferencias "$TOKEN_WEB" POST '{"cuentaOrigenId":101,"cuentaDestinoId":102,"monto":-5}')"
    chequear "transferencia inexistente (404)" 404 "$(http_code http://localhost:8081/transferencias/00000000-0000-0000-0000-000000000000 "$TOKEN_WEB")"

    echo "  -- 5 transferencias simultaneas de 10 (101 -> 102)"
    o_antes=$(saldo 101); d_antes=$(saldo 102)
    for i in 1 2 3 4 5; do
        curl -s -o "$TMP/sim$i.json" -X POST -H "Authorization: Bearer $TOKEN_WEB" -H 'Content-Type: application/json' \
            -d '{"cuentaOrigenId":101,"cuentaDestinoId":102,"monto":10}' http://localhost:8081/transferencias &
    done
    wait
    local completadas=0 ids=""
    for i in 1 2 3 4 5; do
        id=$(python3 -c "import json; print(json.load(open('$TMP/sim$i.json')).get('transaccionId'))")
        ids="$ids $id"
        [ "$(esperar_estado "$id" COMPLETADA 30)" == COMPLETADA ] && completadas=$((completadas + 1))
    done
    echo "  ids:$ids"
    chequear "las 5 quedan COMPLETADA" 5 "$completadas"
    sleep 1
    chequear "saldo origen -50 exacto" "$(python3 -c "print($o_antes - 50)")" "$(saldo 101)"
    chequear "saldo destino +50 exacto" "$(python3 -c "print($d_antes + 50)")" "$(saldo 102)"
    IDS_SIMULTANEAS=$ids

    echo "  -- logs: recorrido de los mensajes y notificacion de clientes"
    if ! $hay_logs; then
        echo "  OMITIDO comprobaciones de logs (en modo local definir LOGS_DIR con un <servicio>.log por servicio)"
        OMITIDAS=$((OMITIDAS + 1))
        return
    fi
    local log_pagos log_cuentas log_clientes
    log_pagos=$(lineas_nuevas pagos "$ini_pagos")
    log_cuentas=$(lineas_nuevas cuentas "$ini_cuentas")
    log_clientes=$(lineas_nuevas clientes "$ini_clientes")
    chequear "pagos publica transferencia.iniciada" 1 "$(grep -c "\[tx=$ID_EXITO\] -> transferencia.iniciada" <<< "$log_pagos")"
    chequear "cuentas recibe iniciada y debita" 1 "$(grep -c "\[tx=$ID_EXITO\] <- transferencia.iniciada" <<< "$log_cuentas")"
    chequear "cuentas recibe debito-realizado y acredita" 1 "$(grep -c "\[tx=$ID_EXITO\] <- transferencia.debito-realizado" <<< "$log_cuentas")"
    chequear "pagos recibe transferencia.completada" 1 "$(grep -c "\[tx=$ID_EXITO\] <- transferencia.completada" <<< "$log_pagos")"
    chequear "clientes recibe transferencia.completada" 1 "$(grep -c "\[tx=$ID_EXITO\] <- transferencia.completada" <<< "$log_clientes")"
    chequear "clientes registra 2 notificaciones" 2 "$(grep -c "\[tx=$ID_EXITO\] \[NOTIFICACION\]" <<< "$log_clientes")"
    chequear "cuentas recibe credito-fallido y compensa" 1 "$(grep -c "\[tx=$ID_COMPENSADA\] <- transferencia.credito-fallido" <<< "$log_cuentas")"
    chequear "pagos recibe transferencia.revertida" 1 "$(grep -c "\[tx=$ID_COMPENSADA\] <- transferencia.revertida" <<< "$log_pagos")"
}

reinicio_pagos() {
    echo "== Reiniciar SOLO pagos: los ids son UUID y no se repiten (ya no hay contador)"
    if ! kafka_listo; then
        chequear "Kafka disponible" true false
        return
    fi
    local id_a id_b o_antes d_antes
    id_a=$(transferir 101 102 20)
    chequear "transferencia previa al reinicio" COMPLETADA "$(esperar_estado "$id_a" COMPLETADA 30)"
    reiniciar_servicio pagos
    esperar_eureka PAGOS 90
    chequear "bff-web vuelve a alcanzar a pagos tras el reinicio" 3 "$(calentar_pagos 60)"
    o_antes=$(saldo 101); d_antes=$(saldo 102)
    id_b=$(transferir 101 102 30)
    chequear "id posterior al reinicio es UUID" true "$(es_uuid "$id_b")"
    chequear "id posterior distinto del anterior" true "$([ "$id_a" != "$id_b" ] && echo true || echo false)"
    chequear "transferencia posterior al reinicio" COMPLETADA "$(esperar_estado "$id_b" COMPLETADA 30)"
    sleep 1
    chequear "saldo origen -30 exacto" "$(python3 -c "print($o_antes - 30)")" "$(saldo 101)"
    chequear "saldo destino +30 exacto" "$(python3 -c "print($d_antes + 30)")" "$(saldo 102)"
}

resiliencia() {
    echo "== Circuit Breaker + fallback con cuentas apagado"
    parar_servicio cuentas
    sleep 2
    for _ in 1 2 3 4 5; do http_code http://localhost:8082/api/movil/cuentas/101 "$TOKEN_MOVIL" > /dev/null; done
    chequear "bff-web con cuentas caido" 503 "$(http_code http://localhost:8081/api/web/cuentas/101 "$TOKEN_WEB")"
    chequear "bff-movil con cuentas caido" 503 "$(http_code http://localhost:8082/api/movil/cuentas/101 "$TOKEN_MOVIL")"
    chequear "bff-movil mensaje del fallback" "El servicio cuentas no esta disponible en este momento" "$(json "d['mensaje']")"
    chequear "bff-cajero saldo con cuentas caido" 503 "$(http_code http://localhost:8083/api/cajero/cuentas/101/saldo "$TOKEN_CAJERO")"
    chequear "bff-cajero retiro con cuentas caido" 503 "$(http_code http://localhost:8083/api/cajero/cuentas/101/retiro "$TOKEN_CAJERO" POST '{"monto":10}')"
    echo "  -- recuperacion (reinicio de cuentas)"
    iniciar_servicio cuentas
    chequear "bff-movil se recupera solo" 200 "$(esperar_codigo http://localhost:8082/api/movil/cuentas/101 "$TOKEN_MOVIL" 200 60)"
    cerrar_circuitos 60

    echo "== Circuit Breaker + fallback con pagos apagado"
    parar_servicio pagos
    sleep 2
    chequear "POST /transferencias con pagos caido" 503 "$(http_code http://localhost:8081/transferencias "$TOKEN_WEB" POST '{"cuentaOrigenId":101,"cuentaDestinoId":102,"monto":1}')"
    chequear "mensaje del fallback" "El servicio pagos no esta disponible en este momento" "$(json "d['mensaje']")"
    iniciar_servicio pagos
    chequear "bff-web se recupera solo (3 transferencias de 1 con 202)" 3 "$(calentar_pagos 40)"

    echo "== Fallback degradado con clientes apagado"
    parar_servicio clientes
    sleep 2
    chequear "bff-web responde 200 sin clientes" 200 "$(http_code http://localhost:8081/api/web/cuentas/101 "$TOKEN_WEB")"
    chequear "bff-web sin nombre (fallback)" None "$(json "d['nombre']")"
    chequear "bff-movil responde 200 sin clientes" 200 "$(http_code http://localhost:8082/api/movil/cuentas/101 "$TOKEN_MOVIL")"
    iniciar_servicio clientes
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
    cerrar_circuitos 40
    registro; seguridad; retiro; payload; salud; saga; reinicio_pagos; resiliencia
    cerrar_circuitos 40
else
    "$SECCION"
fi

echo
echo "RESUMEN: $((TOTAL - FALLAS))/$TOTAL verificaciones OK (modo $MODO, secciones omitidas: $OMITIDAS)"
rm -rf "$TMP"
[ "$FALLAS" -eq 0 ]
