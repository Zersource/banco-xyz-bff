# Banco XYZ: microservicios con Spring Cloud, Kafka y Spring Batch (EFT)

> **Borrador.** Describe el estado de la rama `eft-final`.

Proyecto de Backend III (Duoc UC). Parte del BFF de Exp2, lo separa en microservicios (Exp3) y le suma
cuentas, clientes y pagos como servicios independientes, con una saga de transferencias sobre Kafka y el
proceso batch del Exp1.

## Arquitectura (10 apps)

| App | Puerto | Rol |
|---|---|---|
| `eureka-server` | 8761 | Registro de servicios |
| `config-server` | 8888 | Configuracion centralizada (modo `native`, archivos en `config-repo/`) |
| `auth-server` | 9000 | Servidor OAuth2: emite tokens `client_credentials` (uno por canal) y publica el JWKS |
| `cuentas` | 8084 | Dueno de los saldos, movimientos y transacciones. Ejecuta los pasos de debito, credito y compensacion de la saga |
| `clientes` | 8085 | Datos del titular de cada cuenta (nombre, edad). Notifica (log) las transferencias completadas |
| `pagos` | 8086 | Dueno de las transferencias: las recibe, las guarda (H2) y lanza la saga por Kafka |
| `bff-web` | 8081 | BFF canal web (scope `web`): cuentas completas, transacciones y transferencias |
| `bff-movil` | 8082 | BFF canal movil (scope `movil`): respuesta liviana |
| `bff-cajero` | 8083 | BFF canal cajero (scope `cajero`): saldo y retiro |
| `batch` | n/a | Spring Batch (CLI): transacciones diarias, intereses mensuales y estados de cuenta anuales |

```
cliente --token--> bff-web / bff-movil / bff-cajero --(Eureka + RestClient + token del canal + Circuit Breaker)-->
                     cuentas   clientes   pagos (solo desde bff-web)

pagos --Kafka--> cuentas --Kafka--> pagos, clientes        (saga de transferencias, ver KAFKA_TOPICS.md)
```

- Todos los servicios se registran en Eureka y toman su configuracion del Config Server (`config-repo/`).
- `cuentas`, `clientes`, `pagos` y los 3 BFF son *resource servers* OAuth2: validan la firma del token con
  el JWKS del auth-server y exigen el scope del canal.
- Cada BFF llama a los servicios con su propio token `client_credentials`, protegido con Circuit Breaker
  (Resilience4j) y un fallback: un 503 controlado si el servicio no responde. `clientes` es dato accesorio:
  si cae, web y movil responden igual pero sin nombre.
- `GET /actuator/health` de `cuentas`, `clientes` y `pagos` responde sin token (healthcheck de las imagenes).
- El `transaccionId` es un UUID que genera `pagos` y es la key de cada mensaje de Kafka.

## Saga de transferencias

`POST /transferencias` (bff-web) -> `pagos` guarda la transferencia como PENDIENTE y publica
`transferencia.iniciada` -> `cuentas` debita (atomico) -> `cuentas` acredita el destino -> `transferencia.completada`
(`pagos` marca COMPLETADA y `clientes` notifica). Si el debito falla queda FALLIDA; si el credito falla
(cuenta destino inexistente) `cuentas` devuelve el debito y queda REVERTIDA. Los topics, quien publica y
quien consume cada uno estan en [`KAFKA_TOPICS.md`](KAFKA_TOPICS.md).

## Como levantar en local

Requisitos: Java 21, Maven 3.9 y un Kafka local en modo KRaft escuchando en `localhost:9092`
(binario oficial de Apache Kafka, sin Docker; `bin/kafka-storage.sh format` y `bin/kafka-server-start.sh
config/kraft/server.properties`). Los topics se crean solos al arrancar los servicios.

```bash
# 1. compilar cada modulo (genera <modulo>/target/<modulo>-1.0.0.jar)
for m in eureka-server config-server auth-server cuentas clientes pagos bff-web bff-movil bff-cajero; do
  (cd $m && mvn clean verify); done

# 2. levantar en este orden (cada uno en su terminal o con nohup ... &)
java -jar eureka-server/target/eureka-server-1.0.0.jar
java -jar config-server/target/config-server-1.0.0.jar
java -jar auth-server/target/auth-server-1.0.0.jar
# esperar a que los tres respondan, luego:
java -jar cuentas/target/cuentas-1.0.0.jar
java -jar clientes/target/clientes-1.0.0.jar
java -jar pagos/target/pagos-1.0.0.jar
java -jar bff-web/target/bff-web-1.0.0.jar
java -jar bff-movil/target/bff-movil-1.0.0.jar
java -jar bff-cajero/target/bff-cajero-1.0.0.jar
```

El Config Server debe estar arriba antes que los demas: la configuracion (puertos, JWKS, Kafka, token y
Circuit Breaker de cada BFF) se la piden a el. Un servicio recien arrancado puede tardar hasta ~1 minuto en
ser visible para los otros (cache de Eureka y del balanceador).

Pedir un token y usarlo:

```bash
TOKEN=$(curl -s -u cliente-web:WEB-KEY-2024 -d grant_type=client_credentials -d scope=web \
  localhost:9000/oauth2/token | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')
curl -H "Authorization: Bearer $TOKEN" localhost:8081/api/web/cuentas/101
curl -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"cuentaOrigenId":101,"cuentaDestinoId":102,"monto":500}' localhost:8081/transferencias
```

Clientes OAuth2 (secreto en `auth-server`): `cliente-web` / `WEB-KEY-2024` (scope `web`),
`cliente-movil` / `MOVIL-KEY-2024` (`movil`), `cliente-cajero` / `CAJERO-KEY-2024` (`cajero`).

### Con Docker

`docker-compose.yaml` (y `docker-compose.escala.yaml`) los mantiene la rama `eft-docker`; levanta la infra,
`cuentas`, `clientes`, los 3 BFF y un Kafka. Al momento de este borrador `pagos` todavia no tiene Dockerfile
ni servicio en el compose.

## Endpoints

| Servicio | Endpoint | Scope |
|---|---|---|
| bff-web | `GET /api/web/cuentas`, `GET /api/web/cuentas/{id}`, `GET /api/web/transacciones` | `web` |
| bff-web | `POST /transferencias` (202), `GET /transferencias/{id}` | `web` |
| bff-movil | `GET /api/movil/cuentas/{id}` | `movil` |
| bff-cajero | `GET /api/cajero/cuentas/{id}/saldo`, `POST /api/cajero/cuentas/{id}/retiro` | `cajero` |
| cuentas | `GET /cuentas`, `/cuentas/{id}`, `/cuentas/{id}/saldo`, `/cuentas/movimientos`, `/cuentas/{id}/movimientos`, `/transacciones`, `/transacciones/ultimas`; `POST /cuentas/{id}/retiro` | cualquier canal (el retiro, solo `cajero`) |
| clientes | `GET /clientes`, `GET /clientes/{cuentaId}` | cualquier canal |
| pagos | `POST /transferencias`, `GET /transferencias/{id}` | `web` |

Errores: 401 sin token o invalido, 403 con el scope de otro canal, 404 cuenta/transferencia inexistente,
400 validacion o saldo insuficiente, 503 servicio caido (fallback del Circuit Breaker).

## Batch

El proyecto de Spring Batch vive en [`batch/`](batch/) (importado de
https://github.com/Zersource/Banco-xyz-batch---S1, rama `main`). Es una aplicacion de linea de comandos
independiente, no se registra en Eureka:

```bash
cd batch && mvn clean verify
java -jar target/banco-xyz-batch-1.0.0.jar transacciones --spring.profiles.active=dev,semana_3
java -jar target/banco-xyz-batch-1.0.0.jar intereses --spring.profiles.active=dev,semana_3
java -jar target/banco-xyz-batch-1.0.0.jar estados-cuenta --spring.profiles.active=dev,semana_3
```

Ver `batch/README.md`.

## Pruebas

- `mvn clean verify` en cada modulo (tests en `cuentas`, `pagos` y `batch`).
- `prueba-e2e.sh`: prueba de punta a punta contra el stack levantado (seguridad por canal, retiro,
  peso de cada canal, saga, reinicio de `pagos`, Circuit Breaker). Sale con codigo distinto de 0 si algo falla.
  `MODO=local` (por defecto) o `MODO=docker`; `LOGS_DIR` (opcional) para revisar el recorrido de los
  mensajes en los logs. Detalle en la cabecera del script.
- Evidencia de ejecucion en `evidencia/eft/` (saga, topics, Circuit Breaker, peso de payloads, batch).

## Limitaciones conocidas

1. **H2 en memoria por replica en `cuentas` y `pagos`.** Los saldos, las transferencias y el estado se
   pierden al reiniciar y no se comparten entre replicas. Por eso el escalado consistente aplica hoy a los
   BFF (sin estado propio). El siguiente paso para escalar `cuentas` y `pagos` es un PostgreSQL compartido.
2. **El registro de pasos de la saga vive en memoria en `cuentas`** (`EstadoSagaRepository`): es el guard de
   idempotencia ante mensajes reentregados, pero se pierde al reiniciar `cuentas`.
3. **Secretos de los clientes OAuth2 en claro** en `config-repo/` (y en el `auth-server`). En produccion irian
   en un gestor de secretos (AWS Secrets Manager, Vault).
4. El Config Server usa el modo `native` con los archivos empaquetados en su JAR; cambiar la configuracion
   exige recompilar y reiniciar `config-server`.

## Documentacion

`PROPUESTA_TECNICA*.md` por semana, `KAFKA_TOPICS.md` y `README_S7_ADDENDUM.md` (historico de la saga con JMS,
reemplazada por Kafka).
