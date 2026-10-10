# Topics de Kafka (saga de transferencias)

Broker por defecto `localhost:9092` (`kafka:9092` con el perfil `docker`; la variable de entorno
`KAFKA_BOOTSTRAP_SERVERS` tiene prioridad), configurado en
`config-server/.../config-repo/application.yml` y `application-docker.yml`. Todos los topics se crean
de forma explicita (beans `NewTopic`) con **3 particiones y replicacion 1** (si ya existen no se vuelven a crear), para que al escalar un
servicio a varias instancias el consumo se reparta entre ellas.

La **key** de cada mensaje es el `transaccionId`, un UUID que genera `pagos` y viaja como texto (todos los eventos de una transferencia caen en la misma
particion y se procesan en orden). El **value** es un `EventoTransferencia` en JSON
(`transaccionId` (String), `cuentaOrigenId`, `cuentaDestinoId`, `monto`, `tipoEvento`, `motivo`); cada servicio
tiene su propia copia de la clase.

| Topic | Publica | Consume (group-id) | Que significa |
|---|---|---|---|
| `transferencia.iniciada` | pagos | cuentas (`cuentas-saga`) | Paso 1: hay que debitar la cuenta origen |
| `transferencia.debito-realizado` | cuentas | cuentas (`cuentas-saga`) | Paso 2: el debito se aplico, hay que acreditar el destino |
| `transferencia.debito-fallido` | cuentas | pagos (`pagos-estado`) | Fin: fondos insuficientes u origen inexistente (estado FALLIDA) |
| `transferencia.credito-fallido` | cuentas | cuentas (`cuentas-saga`) | Compensacion: el destino no se pudo acreditar, hay que devolver el debito |
| `transferencia.completada` | cuentas | pagos (`pagos-estado`), clientes (`clientes-notificaciones`) | Fin feliz: pagos marca COMPLETADA, clientes notifica a los titulares |
| `transferencia.revertida` | cuentas | pagos (`pagos-estado`) | Fin: se compenso el debito (estado REVERTIDA) |

```
bff-web --HTTP--> pagos --iniciada--> cuentas --debito-realizado--> cuentas --completada--> pagos, clientes
                                         |                              |
                                         +--debito-fallido--> pagos     +--credito-fallido--> cuentas --revertida--> pagos
```

Los group-id son fijos por servicio (`pagos-estado`, `cuentas-saga`, `clientes-notificaciones`) y estan en
`config-repo/<servicio>.yml`. Con dos instancias de un mismo servicio comparten el group-id y Kafka reparte
las particiones entre ellas.

Idempotencia: `cuentas` guarda el paso de cada transferencia (`EstadoSagaRepository`, en memoria, con
compare-and-set) y ignora un mensaje reentregado; `pagos` solo cambia el estado de una transferencia que
sigue PENDIENTE.
