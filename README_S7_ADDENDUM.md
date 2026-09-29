# S7 — Saga de transferencias con JMS (addendum a este README)

## Objetivo

Esta semana se agregó una arquitectura orientada a eventos para las
transacciones del sistema (transferencias entre cuentas), implementando el
patrón Saga en su variante coreografiada sobre JMS (ActiveMQ Artemis
embebido). El detalle de diseño está en `PROPUESTA_TECNICA_S7.md`.

## Estructura del código nuevo

Todo vive en `bff-web/src/main/java/com/duoc/bancoxyzbff/transferencia/`:
transferencia/
├── config/ JmsConfig — nombres de colas y conversor JSON
├── controller/ TransferenciaController — POST/GET /transferencias
├── dto/ Request/Response del endpoint
├── evento/ EventoTransferencia (DTO de mensaje) y el productor
├── listener/ 4 listeners: Debito, Credito, Compensacion, EstadoTransaccion
├── model/ Transaccion (entidad JPA) y su enum de estado
├── repository/ TransaccionRepository (JPA) y CuentaSaldoPuerto (puerto
│ hacia el saldo, con operaciones atómicas debitar/acreditar)
└── service/ TransferenciaService + su implementación


Evidencia de ejecución (salidas de consola reales) en
`evidencia/s7_saga_jms/`:
- `logs_concurrencia_antes.txt` / `logs_concurrencia_despues.txt` — prueba
  de la corrección de concurrencia, antes y después del fix.
- `logs_resilience4j.txt` — secuencia completa del Circuit Breaker
  (CLOSED → OPEN → HALF_OPEN → CLOSED) con el stack completo levantado.
- `prueba_concurrencia.sh` — script para repetir la prueba de
  transferencias simultáneas.

## Cómo ejecutar

1. Requiere Java 21 y Maven. No requiere Docker (el broker JMS es
   embebido).
2. Levantar en orden: `eureka-server`, `config-server`, `bff-web`, y
   opcionalmente `bff-movil`/`bff-cajero` si se quiere probar el Circuit
   Breaker de S6 (`mvn spring-boot:run` en cada uno).
3. Probar la saga contra `bff-web` (puerto por defecto del proyecto):

```bash
curl -X POST http://localhost:<puerto-bff-web>/transferencias \
  -H "Content-Type: application/json" \
  -d '{"cuentaOrigenId":101,"cuentaDestinoId":102,"monto":50}'

curl http://localhost:<puerto-bff-web>/transferencias/<id>
```

4. Correr los tests: `mvn -pl bff-web clean test` (7 tests, incluye el
   test de concurrencia con 100 hilos).
