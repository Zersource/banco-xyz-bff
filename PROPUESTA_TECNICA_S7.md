# Propuesta técnica — Semana 7 (Exp3)
## Configurando tolerancia a fallos y arquitectura de eventos con microservicios en la nube

### 1. Arquitectura de eventos elegida

Para las transacciones del sistema (transferencias entre cuentas) elegí el
**Patrón Saga en su variante coreografiada**, implementado con **JMS sobre
ActiveMQ Artemis embebido** (sin infraestructura Docker adicional).

**Por qué Saga y no Event Sourcing:** el caso de uso es coordinar una
transacción distribuida con dos pasos (débito en cuenta origen, crédito en
cuenta destino) que puede fallar a mitad de camino y necesita una acción
compensatoria (revertir el débito). Event Sourcing resuelve otro problema
(reconstruir estado desde el historial completo de eventos) que hubiera
exigido rediseñar cómo se guarda el saldo, excediendo el alcance de esta
semana.

**Por qué coreografía y no orquestación:** con dos pasos y una
compensación, un orquestador central era infraestructura adicional sin
beneficio real — cada listener ya sabe qué evento publicar a continuación
según el resultado de su propio paso.

**Por qué JMS y no Kafka:** el caso es comunicación punto a punto en un
flujo transaccional bien definido (cada mensaje lo procesa un único
consumidor, en orden), que es justo el escenario para el que la guía de la
semana recomienda JMS. Kafka aporta valor con múltiples consumidores
leyendo el mismo evento en paralelo, que no es este caso. Artemis embebido
además evitó sumar infraestructura Docker sobre una semana que ya traía
bastante (Eureka, config-server, Circuit Breaker de S6).

### 2. Tópicos/colas y eventos de la solución

Ver diagrama adjunto (`saga_transferencia.png`). Resumen de las 6 colas
punto a punto:

| Cola | Evento | Publica | Consume |
|---|---|---|---|
| `queue.transferencia.iniciada` | `TransferenciaIniciada` | `TransferenciaController` | `DebitoListener` |
| `queue.debito.realizado` | `DebitoRealizado` | `DebitoListener` | `CreditoListener` |
| `queue.transferencia.fallida` | `DebitoFallido` | `DebitoListener` | `EstadoTransaccionListener` |
| `queue.transferencia.completada` | `TransferenciaCompletada` | `CreditoListener` | `EstadoTransaccionListener` |
| `queue.transferencia.compensacion` | `CreditoFallido` | `CreditoListener` | `CompensacionListener` |
| `queue.transferencia.revertida` | `TransferenciaRevertida` | `CompensacionListener` | `EstadoTransaccionListener` |

Cada `@JmsListener` corre con `concurrency = "3-5"` (pasos principales) o
`"2-3"` (auditoría), es decir, varios hilos consumidores en paralelo por
cola — la escalabilidad se demuestra disparando varias transferencias a la
vez y viendo distintos hilos (`ntContainer#2-1`, `#2-2`, `#2-3`) procesando
en paralelo (ver `evidencia/s7_saga_jms/logs_concurrencia_despues.txt`).

### 3. Tolerancia a fallos (Resilience4j)

El Circuit Breaker + Fallback entre `bff-movil`/`bff-cajero` y `bff-web`
viene de la Semana 6 y no requiere cambios para esta actividad. Se
verificó de nuevo esta semana con el stack completo levantado (Eureka,
config-server, bff-web, bff-movil, bff-cajero): secuencia completa
CLOSED → OPEN → HALF_OPEN → CLOSED capturada en
`evidencia/s7_saga_jms/logs_resilience4j.txt`, incluyendo el detalle de que
un HALF_OPEN → OPEN intermedio se debió a la latencia normal del caché de
Eureka (refresh cada 30s) y no a una falla del propio breaker.

### 4. Concurrencia: un riesgo real, encontrado y corregido

Al activar concurrencia en los listeners para demostrar escalabilidad,
detecté que `CuentaSaldoPuerto` originalmente leía y escribía el saldo en
dos pasos separados (`obtenerSaldo` + `actualizarSaldo`), lo que permitía
que varios hilos pisaran la escritura del otro bajo transferencias
simultáneas sobre la misma cuenta.

**Evidencia del problema** (`logs_concurrencia_antes.txt`): 4
transferencias simultáneas de $10/$20/$30/$40 sobre la cuenta 105 debieron
restar $100 en total; en la mayoría de las 5 corridas de prueba, el saldo
final quedó por encima de lo esperado porque se perdieron débitos
intermedios (4 de 5 corridas con descuadre).

**Corrección aplicada:** se reemplazó el patrón leer-y-escribir por dos
operaciones atómicas y sincronizadas, `debitar()` y `acreditar()`, que
validan y modifican el saldo en un solo paso protegido. Se agregó además
un test de concurrencia real con 100 hilos simultáneos
(`CuentaSaldoPuertoImplConcurrenciaTest`) y se repitió la prueba de
transferencias simultáneas 5 veces más.

**Evidencia de la corrección** (`logs_concurrencia_despues.txt`): 5 de 5
corridas con saldos exactos tras el fix, más verificación de que los
escenarios de camino feliz, fondos insuficientes, compensación (destino
inexistente) y débito fallido (origen inexistente) siguen funcionando
correctamente, sin reentregas JMS en bucle gracias a un guard de
idempotencia agregado en cada listener.

**Deuda técnica heredada, documentada y no modificada:** durante la
revisión se encontró que `CuentaServiceImpl.realizarRetiro()` (usado por el
retiro de `bff-cajero`, fuera del módulo de esta semana) modifica el mismo
`CuentaRepository` con el mismo patrón no sincronizado. La corrección
aplicada protege las transferencias entre sí, pero no contra una colisión
entre un retiro y una transferencia simultánea sobre la misma cuenta. No
se modificó por exceder el alcance de esta actividad (es código del
proyecto base, no de la saga). Recomendación para una iteración futura:
que retiros y transferencias pasen por la misma operación atómica de
`CuentaSaldoPuerto`.

### 5. Relación con lo entregado en semanas anteriores

- Arquitectura de 3 microservicios (`bff-web`, `bff-movil`, `bff-cajero`)
  registrados en Eureka (S6) sin cambios.
- Autenticación JWT (S5) sin cambios.
- Todo lo nuevo vive dentro de `bff-web`, en el módulo `transferencia`, sin
  tocar el resto del sistema salvo el hallazgo documentado arriba.
