# Banco XYZ — Migracion de sistema legacy a microservicios con Spring Cloud

> Evaluacion Final Transversal (EFT) — PBY2203 Desarrollo Backend III, DuocUC

Proyecto que migra los procesos legacy del Banco XYZ a una arquitectura de microservicios con Spring Boot 3.3.4, Java 21, Spring Cloud, Apache Kafka, Spring Batch y Docker.

## Arquitectura (10 aplicaciones + Kafka)

```
cliente ──token──► bff-web / bff-movil / bff-cajero
                        │ (Eureka + RestClient + Circuit Breaker)
                        ▼
                   cuentas   clientes   pagos
                        │                 │
                        └──── Kafka ──────┘  (saga de transferencias)
```

| Aplicacion | Puerto | Rol |
|---|---|---|
| `config-server` | 8888 | Configuracion centralizada (modo `native`, archivos en `config-repo/`) |
| `eureka-server` | 8761 | Service Discovery |
| `auth-server` | 9000 | OAuth2 con Spring Authorization Server: `client_credentials`, JWT 15 min, JWKS, RSA |
| `kafka` | 9094 | Broker Kafka en modo KRaft (sin Zookeeper) |
| `cuentas` | 8084 | Cuentas, saldos, movimientos. Ejecuta debito, credito y compensacion de la saga |
| `clientes` | 8085 | Datos del titular. Notifica transferencias completadas |
| `pagos` | 8086 | Transferencias: las recibe, guarda en H2 y lanza la saga por Kafka |
| `bff-web` | 8081 | BFF canal web (scope `web`): datos completos, transacciones, transferencias |
| `bff-movil` | 8082 | BFF canal movil (scope `movil`): respuesta liviana |
| `bff-cajero` | 8083 | BFF canal cajero (scope `cajero`): saldo y retiro |
| `batch` | — | Spring Batch (CLI): 3 jobs de procesamiento legacy. Gateado por profile |

## Tecnologias principales

- **Spring Boot 3.3.4** / Java 21 / Maven (modulos independientes, sin root POM)
- **Spring Cloud**: Eureka (Service Discovery), Config Server (native)
- **Spring Authorization Server 1.3.2**: OAuth2 `client_credentials`, scopes por canal, JWT con RSA
- **Apache Kafka 3.7**: saga de transferencias con 6 topics × 3 particiones
- **Spring Batch 5**: 3 jobs con particionamiento, retry, skip y manejo de errores
- **Resilience4j**: Circuit Breaker en los BFF (CLOSED → OPEN → HALF_OPEN → CLOSED)
- **Docker**: multi-stage builds, Compose con `depends_on` + `service_healthy`, escalado horizontal

## Saga de transferencias (Kafka)

`POST /transferencias` → `pagos` guarda como PENDIENTE → publica `transferencia.iniciada` → `cuentas` debita → acredita → `transferencia.completada` → `pagos` marca COMPLETADA, `clientes` notifica.

Si el debito falla: FALLIDA. Si el credito falla (cuenta destino inexistente): `cuentas` devuelve el debito → REVERTIDA.

Detalle de topics, productores, consumidores y formato de mensajes en [`KAFKA_TOPICS.md`](KAFKA_TOPICS.md).

## Spring Batch (3 jobs)

| Job | Descripcion | Datos de entrada |
|---|---|---|
| `dailyTransactionsJob` | Transacciones diarias con filtro de anomalias | `transacciones.csv` |
| `monthlyInterestJob` | Calculo de intereses mensuales | `cuentas.csv` |
| `annualStatementJob` | Estados de cuenta anuales | `movimientos.csv` |

Los 3 jobs procesan datos del legacy bancario (`data/semana_3/`), con particionamiento (3 workers), skip de registros invalidos y retry ante fallos transitorios. Ver [`batch/README.md`](batch/README.md).

## BFF por canal

| Canal | BFF | Datos expuestos | Seguridad |
|---|---|---|---|
| Web | `bff-web` | Cuentas completas, transacciones, transferencias | scope `web` |
| Movil | `bff-movil` | Cuenta resumida (liviana) | scope `movil` |
| Cajero | `bff-cajero` | Solo saldo y retiro | scope `cajero` |

Cada BFF tiene Circuit Breaker contra `cuentas` y `clientes`. Si un servicio cae, responde 503 con fallback. `clientes` es dato accesorio: si cae, web y movil responden sin nombre.

## Como ejecutar

### Con Docker (recomendado)

```bash
git clone --branch eft-final https://github.com/Zersource/banco-xyz-bff.git
cd banco-xyz-bff
docker compose up -d --build       # 10 servicios + kafka
docker compose ps                  # esperar healthy
MODO=docker /bin/bash ./prueba-e2e.sh   # 95/95 OK
```

Batch (separado, por profile):

```bash
docker compose --profile batch run --rm -e JOB=todos batch
```

### En local

Requisitos: Java 21, Maven 3.9, Kafka local en `localhost:9092` (KRaft).

```bash
for m in eureka-server config-server auth-server cuentas clientes pagos bff-web bff-movil bff-cajero; do
  (cd $m && mvn clean verify); done
# Levantar en orden: eureka → config → auth → cuentas/clientes/pagos → bff-*
```

### Escalabilidad horizontal

```bash
docker compose -f docker-compose.yaml -f docker-compose.escala-bff.yaml up -d
# → 2 replicas de bff-web + nginx en localhost:8080
```

## Endpoints

| Servicio | Endpoint | Metodo | Scope |
|---|---|---|---|
| bff-web | `/api/web/cuentas`, `/api/web/cuentas/{id}` | GET | `web` |
| bff-web | `/api/web/transacciones` | GET | `web` |
| bff-web | `/transferencias` | POST/GET | `web` |
| bff-movil | `/api/movil/cuentas/{id}` | GET | `movil` |
| bff-cajero | `/api/cajero/cuentas/{id}/saldo` | GET | `cajero` |
| bff-cajero | `/api/cajero/cuentas/{id}/retiro` | POST | `cajero` |

Obtener token: `POST http://localhost:9000/oauth2/token` con Basic Auth (`cliente-web:WEB-KEY-2024`), `grant_type=client_credentials`, `scope=web`.

## Pruebas

- `mvn clean verify` en cada modulo (tests unitarios en `cuentas`, `pagos`, `batch`)
- `prueba-e2e.sh`: 95 verificaciones (seguridad, saga, Circuit Breaker, peso de payloads, reinicio)
- Evidencia de ejecucion en `evidencia/eft_docker/final/`

## Documentacion

- [`instrucciones.md`](instrucciones.md) — Pasos para ejecutar y probar cada componente
- [`despliegue.md`](despliegue.md) — Guia de despliegue en EC2/AWS con Docker Compose
- [`KAFKA_TOPICS.md`](KAFKA_TOPICS.md) — Topics, productores, consumidores y formato de mensajes
- [`batch/README.md`](batch/README.md) — Detalle de los 3 jobs de Spring Batch
- `PROPUESTA_TECNICA*.md` — Propuestas tecnicas por semana

## Limitaciones conocidas

1. **H2 en memoria** en `cuentas` y `pagos`: saldos y transferencias se pierden al reiniciar. El escalado consistente aplica a los BFF (stateless). Siguiente paso: PostgreSQL compartido.
2. **Estado de saga en memoria** (`EstadoSagaRepository` en `cuentas`): guard de idempotencia que se pierde al reiniciar.
3. **Secretos en claro** en `config-repo/`. En produccion: AWS Secrets Manager o Vault.
4. **Clave RSA en memoria** en `auth-server`: al reiniciarlo, tokens en cache quedan invalidos hasta su vencimiento (15 min).
5. **Config Server modo native**: cambiar configuracion exige recompilar y reiniciar.
