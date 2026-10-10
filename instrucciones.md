# Instrucciones de ejecucion y pruebas — Banco XYZ (EFT)

Repositorio: `https://github.com/Zersource/banco-xyz-bff` — rama `eft-final`

---

## Requisitos previos

| Componente | Version probada | Minimo |
|---|---|---|
| Docker Engine | 29.6.2 | 20.10+ |
| Docker Compose | v5.3.1 | **v2.24** (los overrides de escala usan `!override`) |
| bash | 3.2 (macOS) | 3.2+ |
| curl | 8.x | cualquier version reciente |
| python3 | 3.12 | 3.6+ (solo para parsear JSON en el e2e) |

Memoria asignada a Docker: **al menos 4 GB** (el stack completo usa ~3.9 GB en reposo con 10 contenedores).

Puertos libres en el host: `8081-8086`, `8761`, `8888`, `9000` y `9094`.
Las pruebas de escala usan ademas `8080`, `8090-8093`.

Arquitectura: ningun Dockerfile ni compose fija `platform`. Las imagenes se construyen para la
arquitectura de la maquina donde se ejecutan (probado en arm64/Apple Silicon y las imagenes base
tienen variante amd64 para EC2).

---

## 1. Clonar el repositorio

```bash
git clone --branch eft-final https://github.com/Zersource/banco-xyz-bff.git
cd banco-xyz-bff
```

---

## 2. Levantar el stack completo (10 servicios + Kafka)

```bash
docker compose up -d --build
```

Esperar ~30 segundos (con cache de build) hasta que los 10 contenedores queden `healthy`:

```bash
docker compose ps
```

Salida esperada: 10 servicios `Up ... (healthy)`:

| Servicio | Puerto | Rol |
|---|---|---|
| config-server | 8888 | Configuracion centralizada (modo native) |
| eureka-server | 8761 | Service Discovery |
| auth-server | 9000 | OAuth2 (client_credentials, JWT, JWKS) |
| kafka | 9094 | Broker Kafka (KRaft, sin Zookeeper) |
| cuentas | 8084 | Microservicio de cuentas, saldos, saga debito/credito |
| clientes | 8085 | Microservicio de clientes, notificaciones |
| pagos | 8086 | Microservicio de transferencias, orquestador saga |
| bff-web | 8081 | BFF canal web (scope `web`) |
| bff-movil | 8082 | BFF canal movil (scope `movil`) |
| bff-cajero | 8083 | BFF canal cajero (scope `cajero`) |

Orden de arranque: `config-server` → `eureka-server` → `auth-server` + `kafka` → `cuentas`, `clientes`, `pagos` → los 3 BFF. El `depends_on` con `service_healthy` del compose se encarga de esto automaticamente.

---

## 3. Prueba end-to-end (95 verificaciones)

```bash
MODO=docker /bin/bash ./prueba-e2e.sh
```

Salida esperada:

```
RESUMEN: 95/95 verificaciones OK (modo docker, secciones omitidas: 0)
```

El script verifica: seguridad OAuth2 por canal (tokens, scopes, 401/403), endpoints de los 3 BFF, saga de transferencias (exito, fallo por fondos, compensacion por cuenta destino inexistente), reinicio de `pagos` con UUID, Circuit Breaker (transiciones CLOSED → OPEN → HALF_OPEN → CLOSED) y peso de payloads por canal. Duracion aproximada: 3 minutos.

---

## 4. Probar seguridad OAuth2 manualmente

Obtener un token del canal web:

```bash
TOKEN=$(curl -s -u cliente-web:WEB-KEY-2024 \
  -d grant_type=client_credentials -d scope=web \
  http://localhost:9000/oauth2/token \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')
```

Usar el token:

```bash
# Listar cuentas (canal web)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/web/cuentas

# Consultar cuenta especifica
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/web/cuentas/101

# Sin token → 401
curl -v http://localhost:8081/api/web/cuentas/101

# Token de otro canal → 403
TOKEN_MOVIL=$(curl -s -u cliente-movil:MOVIL-KEY-2024 \
  -d grant_type=client_credentials -d scope=movil \
  http://localhost:9000/oauth2/token \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')
curl -H "Authorization: Bearer $TOKEN_MOVIL" http://localhost:8081/api/web/cuentas/101
# → 403 Forbidden
```

Clientes OAuth2 registrados en `auth-server`:

| Cliente | Secreto | Scope |
|---|---|---|
| `cliente-web` | `WEB-KEY-2024` | `web` |
| `cliente-movil` | `MOVIL-KEY-2024` | `movil` |
| `cliente-cajero` | `CAJERO-KEY-2024` | `cajero` |

---

## 5. Probar la saga de transferencias (Kafka)

Con un token web:

```bash
# Transferencia exitosa (101 → 102)
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"cuentaOrigenId":101,"cuentaDestinoId":102,"monto":25}' \
  http://localhost:8081/transferencias

# Consultar estado (usar el id devuelto)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/transferencias/<id>

# Transferencia a cuenta inexistente → compensacion (REVERTIDA)
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"cuentaOrigenId":101,"cuentaDestinoId":9999,"monto":40}' \
  http://localhost:8081/transferencias
```

Para ver el recorrido de la saga en los logs:

```bash
docker compose logs --no-log-prefix pagos cuentas clientes | grep "<transaccionId>"
```

Flujo de topics Kafka: `transferencia.iniciada` → `transferencia.debito-realizado` → `transferencia.completada` (o `credito-fallido` → `revertida`). Detalle en `KAFKA_TOPICS.md`.

---

## 6. Probar los 3 canales BFF

```bash
# BFF Web (datos completos)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/web/cuentas
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/web/transacciones

# BFF Movil (respuesta liviana)
TOKEN_M=$(curl -s -u cliente-movil:MOVIL-KEY-2024 -d grant_type=client_credentials -d scope=movil \
  http://localhost:9000/oauth2/token | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')
curl -H "Authorization: Bearer $TOKEN_M" http://localhost:8082/api/movil/cuentas/101

# BFF Cajero (solo saldo y retiro)
TOKEN_C=$(curl -s -u cliente-cajero:CAJERO-KEY-2024 -d grant_type=client_credentials -d scope=cajero \
  http://localhost:9000/oauth2/token | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')
curl -H "Authorization: Bearer $TOKEN_C" http://localhost:8083/api/cajero/cuentas/101/saldo
curl -X POST -H "Authorization: Bearer $TOKEN_C" -H "Content-Type: application/json" \
  -d '{"monto":100}' http://localhost:8083/api/cajero/cuentas/101/retiro
```

---

## 7. Probar Spring Batch (3 jobs)

El servicio `batch` esta gateado por profile y no se levanta con el stack principal:

```bash
# Los 3 jobs de una vez
docker compose --profile batch run --rm -e JOB=todos batch

# O uno por uno
docker compose --profile batch run --rm -e JOB=transacciones batch
docker compose --profile batch run --rm -e JOB=intereses batch
docker compose --profile batch run --rm -e JOB=estados-cuenta batch
```

Salida esperada para cada job: `finalizado con EXITO`.

| Job | Que hace | Archivo de salida |
|---|---|---|
| `dailyTransactionsJob` | Carga transacciones diarias, filtra anomalias | `transacciones.csv` |
| `monthlyInterestJob` | Calcula intereses mensuales sobre saldos | `intereses.csv` |
| `annualStatementJob` | Genera estados de cuenta anuales | `cuentas_anuales.csv` |

Los datos de entrada provienen del volumen `batch-data` (datos legacy del banco en `data/semana_3/`).

### 7.1 Prueba de falla permanente (retry agotado)

```bash
docker compose -f docker-compose.yaml \
  -f docker/batch/test/compose.falla-permanente.yaml \
  --profile batch up batch
```

Esperado: 4 intentos (1 + 3 retry) con `ESTADO FAILED`, exit code 1, RestartCount 3:

```bash
docker inspect -f '{{.State.ExitCode}} {{.RestartCount}}' \
  $(docker compose -f docker-compose.yaml \
    -f docker/batch/test/compose.falla-permanente.yaml \
    --profile batch ps -aq batch)
# → 1 3
```

### 7.2 Prueba de falla transitoria (recuperacion)

```bash
/bin/bash docker/batch/test/recuperar_csv.sh
```

Esperado: primer intento falla (CSV ausente), el script copia el CSV, segundo intento exitoso. ExitCode 0, RestartCount 1.

---

## 8. Probar Circuit Breaker (Resilience4j)

El e2e ya cubre esto (seccion de resiliencia, 13/13 checks), pero para reproducirlo manualmente:

```bash
# 1. Detener cuentas
docker compose stop cuentas

# 2. Hacer 3+ llamadas al BFF movil → 503 (fallback)
for i in 1 2 3; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -H "Authorization: Bearer $TOKEN_M" http://localhost:8082/api/movil/cuentas/101
done
# → 503 (CLOSED con failures acumulados, luego OPEN)

# 3. Esperar ~10 s (waitDurationInOpenState) y llamar → 503 (HALF_OPEN intenta, cuentas sigue caido)

# 4. Levantar cuentas
docker compose start cuentas
# Esperar ~30 s (registro en Eureka)

# 5. Llamar de nuevo → 200 (HALF_OPEN → CLOSED)
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $TOKEN_M" http://localhost:8082/api/movil/cuentas/101
```

Ver logs del breaker (ya tiene nivel DEBUG en el compose):

```bash
docker compose logs --no-log-prefix bff-movil | grep CircuitBreaker
```

---

## 9. Probar escalabilidad horizontal de bff-web

```bash
# Levantar 2 replicas de bff-web + nginx como balanceador
docker compose -f docker-compose.yaml -f docker-compose.escala-bff.yaml up -d

# Verificar 2 instancias en Eureka (~60 s)
curl -s http://localhost:8761/eureka/apps/BFF-WEB | grep -c "<instanceId>"
# → 2

# Verificar balanceo round-robin
for i in $(seq 1 10); do
  curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/web/cuentas/101 > /dev/null
done
docker compose logs --no-log-prefix bff-web | grep DispatcherServlet | grep -c "bff-web-1"
docker compose logs --no-log-prefix bff-web | grep DispatcherServlet | grep -c "bff-web-2"
# → ~5 y ~5 (distribucion equilibrada)

# Volver a 1 replica
docker compose up -d --scale bff-web=1 --remove-orphans
```

El balanceador escucha en `http://localhost:8080`. Las replicas directas en `8092` y `8093`.

---

## 10. Bajar el stack

```bash
docker compose down -v --remove-orphans
```

---

## Endpoints disponibles

| Servicio | Endpoint | Metodo | Scope |
|---|---|---|---|
| bff-web | `/api/web/cuentas` | GET | `web` |
| bff-web | `/api/web/cuentas/{id}` | GET | `web` |
| bff-web | `/api/web/transacciones` | GET | `web` |
| bff-web | `/transferencias` | POST | `web` |
| bff-web | `/transferencias/{id}` | GET | `web` |
| bff-movil | `/api/movil/cuentas/{id}` | GET | `movil` |
| bff-cajero | `/api/cajero/cuentas/{id}/saldo` | GET | `cajero` |
| bff-cajero | `/api/cajero/cuentas/{id}/retiro` | POST | `cajero` |
| auth-server | `/oauth2/token` | POST | — |
| auth-server | `/.well-known/jwks.json` | GET | — |
| eureka-server | `/eureka/apps` | GET | — |
| */actuator/health | (todos) | GET | sin token |

Errores: `401` sin token o invalido, `403` scope de otro canal, `404` recurso inexistente, `400` validacion o saldo insuficiente, `503` servicio caido (fallback Circuit Breaker).
