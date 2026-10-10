# Propuesta Técnica — Migración de Procesos Batch del Banco XYZ

        S1, S2 y S3 — Desarrollo Backend III


## 1. Resumen técnico del avance

Durante la semana 1 migré los tres procesos batch legacy del Banco XYZ (COBOL/Shell) a
Spring Batch: transacciones diarias, intereses mensuales y estados de cuenta anuales, cada
uno como un `Job` independiente con su propio Step de limpieza, carga y agregación. Esa
base quedó probada ejecutando `mvn test` contra H2, con políticas de omisión y reintento, y con
tres bugs reales corregidos al ejecutar el proyecto (documentados en el `README.md`).

En la semana 2 llevé esa base al requisito de escalamiento: chunks de tamaño 5 y 3 hilos
en paralelo, vía `TaskExecutor` simple.

Esta semana el foco fue escalado avanzado con datos a volumen real (1000 filas por CSV) y
tolerancia a fallos más formal. El plan inicial era comparar dos enfoques en paralelo:
migrar transacciones a particiones reales y reforzar intereses/estados-cuenta con el mismo
multi-hilo de S2 más un `BackOffPolicy`. Cambió a mitad de camino: al agregar el backoff, el
step de intereses quedó colgado corriendo contra el dataset de 1000 filas. No fue el
volumen de datos, fue una condición de carrera real entre `taskExecutor` y
`faultTolerant()` que Spring Batch no garantiza segura fuera de un Step particionado (el
detalle está en la sección 3). Terminé con transacciones particionado  e intereses/estados-cuenta secuenciales pero con las mismas políticas de skip/retry/backoff, más un benchmark real que justifica el chunk elegido para
cada uno (sección 2.4).

## 2. Análisis y diseño de la implementación

### 2.1 Arquitectura propuesta

Cada Job sigue el mismo patrón: Job → Steps → Reader/Processor/Writer, detallado en el
`README.md`. Un step de limpieza, un step de carga fault-tolerant, y un step de agregación
que genera el resumen final vía SQL. Base de datos: PostgreSQL en producción
(`docker-compose.yml`), H2 para desarrollo y tests (perfil `dev`).

### 2.2 Escalado: particiones para transacciones, secuencial para el resto

**Transacciones diarias** pasó de multi-hilo (S2) a particionado real: un
`TransaccionRangoPartitioner` divide `transacciones.csv` en rangos de líneas (uno por
partición) y `TaskExecutorPartitionHandler` las ejecuta en paralelo sobre el pool de
`BatchThreadingConfig`. Cada partición tiene su propio `FlatFileItemReader`
(`@StepScope`, con `linesToSkip`/`maxItemCount` según su rango), así que ya no necesito
`SynchronizedItemStreamReader` , no hay reader compartido que sincronizar.

**Intereses y estados-cuenta** se quedaron con el multi-hilo de S2 hasta el hallazgo de la
sección 3: combinar `taskExecutor` con `faultTolerant()` en un Step no particionado no es
seguro en Spring Batch. Con 8-10 filas nunca se manifestó; con 1000 filas bajo concurrencia
real, sí. Los dejé secuenciales, con las políticas de skip/retry/backoff intactas. No
migré también esos dos a particiones, el hallazgo apareció avanzada la semana, y
particionar los tres habría triplicado el trabajo de esta sección para una ganancia que el
benchmark (2.4) no alcanza a justificar en Jobs de este volumen.

Todos los parámetros de escalado (`grid-size`, `chunk-size`, `thread-pool-size`) quedaron
como propiedad (`app.batch.*`), para compararlos sin recompilar.

### 2.3 Políticas de reintento y tolerancia a fallos

S2 ya tenía `.retry(TransientDataAccessException.class).retryLimit(3)`, pero sin espera
entre intentos. Agregué un `BackOffPolicy` exponencial (`BatchRetryConfig`, compartido por
los tres Jobs): 200 ms antes del primer reintento, duplicando hasta un tope de 2000 ms.
Reintentar sin esperar contra un error transitorio de conexión cae en el mismo problema que
lo originó; con backoff, el segundo o tercer intento tiene una chance real de encontrar la
conexión restablecida.

La política de omisión se mantiene igual que en S2 (`DatoInvalidoException`,
`FlatFileParseException`, `DuplicateKeyException`, con `noRollback` sobre la primera). El
`skipLimit` sí cambió: pasó de un valor fijo (200, calibrado para los CSV chicos de S1/S2)
a una propiedad por Job, con default calibrado contra datos reales de semana 3.

### 2.4 Benchmark de escalado: metodología y resultados

Con datos de semana 3 (1000 filas por CSV, ~57% con datos corruptos por diseño en
transacciones e intereses, ~39% en estados-cuenta) corrí 10 combinaciones para encontrar la
configuración óptima de cada Job, midiendo con `PerformanceStepListener`.

**Transacciones (particionado) — grid-size × chunk-size × thread-pool-size:**

| grid-size | chunk-size | pool | válidos | omitidos | step más lento (partición) | job total |
|---|---|---|---|---|---|---|
| 1 | 5 | 1 | 401 | 599 | 692 ms | 763 ms |
| 3 | 5 | 3 | 401 | 599 | 386 ms | 473 ms |
| 5 | 5 | 5 | 401 | 599 | 329 ms | 428 ms |
| **3** | **20** | **3** | 401 | 599 | **311 ms** | **405 ms** |

Los conteos válidos/omitidos son idénticos en las 4 filas, particionar no cambia qué filas
son válidas, solo cómo se reparte el trabajo. De 3 a 5 particiones el tiempo mejora poco
(386→329 ms por casi el doble de hilos). Subir el chunk de 5 a 20 con las mismas 3
particiones (405 ms) rindió igual o mejor que subir a 5 particiones (428 ms); el tamaño
del chunk pesa tanto como el número de hilos acá, y es más barato de escalar. Config
elegida: **grid-size=3, chunk-size=20, thread-pool-size=3**.

**Intereses y estados-cuenta (secuencial) — solo chunk-size:**

| Job | chunk-size | válidos | omitidos | step |
|---|---|---|---|---|
| Intereses | 5 | 50 | 950 | 1028 ms |
| Intereses | **20** | 50 | 950 | **800 ms** |
| Intereses | 50 | 50 | 950 | 699 ms |
| Estados-cuenta | 5 | 614 | 386 | 665 ms |
| Estados-cuenta | **20** | 614 | 386 | **516 ms** |
| Estados-cuenta | 50 | 614 | 386 | 469 ms |

Mismo patrón en ambos: la ganancia se concentra entre 5 y 20; de 20 a 50 ya es marginal
(~100 ms y ~47 ms) y no justifica el chunk más grande. Config elegida: **chunk-size=20** en
ambos.

Dato curioso, no un bug: en intereses solo 50 de 1000 filas terminan insertadas. El
catálogo sintético de semana 3 tiene apenas 8 nombres distintos, así que el constraint
`UNIQUE(nombre, saldo_inicial, edad, tipo)` descarta como duplicado el 87.5% de lo que sí
pasa las reglas de negocio y confirma con un número concreto la limitación de
deduplicación por contenido que ya venía anotando desde S1/S2.

## 3. Problemas encontrados en la semana 3 y cómo los solucioné

Otra vez ninguno visible en el código en frío — los tres aparecieron corriendo contra
datos reales de volumen.

**Deadlock combinando multi-hilo con `faultTolerant()` fuera de particiones.** Al correr
`mvn test` después de agregar el backoff, el test de intereses quedó colgado — no lento,
colgado: thread dump con el hilo principal bloqueado en `ResultHolderResultQueue.take()` y
los 3 hilos del pool inactivos. El test sigue usando el CSV chico de 8 filas de semana 1
(lo confirmé corriendo el test aislado, 32 ms), así que no fue el volumen. Fue el backoff:
antes un reintento se disparaba al instante, con backoff exponencial el hilo duerme entre
200 ms y 2000 ms antes de reintentar, y ese delay expuso una condición de carrera que ya
existía desde S2 en esa combinación. Lo arreglé quitando `taskExecutor`/`throttleLimit` de
intereses y estados-cuenta — el mismo principio que ya había motivado particionar
transacciones: retry/skip real solo es seguro bajo paralelismo si el Step está
particionado.

**`skipLimit(200)` insuficiente para datos reales de volumen.** El límite que alcanzaba de
sobra para los 1-2 datos corruptos de S1/S2 se quedó corto para esta semana 3: medí 599 filas
omitidas en transacciones, 950 en intereses, 386 en estados-cuenta. Lo arreglé haciendo
`skipLimit` configurable por Job, con defaults calibrados contra esos conteos reales: 700
en transacciones (ya alcanzaba), 700→1000 en intereses (950 real, más alto de lo que había
estimado), 300→500 en estados-cuenta (386).

**La JVM no cerraba sola al terminar un Job vía CLI.** `JobLauncherRunner` nunca llamaba
`System.exit()`, así que el cierre dependía de que todos los hilos no-daemon murieran
solos y los del pool de `BatchThreadingConfig` quedan vivos esperando trabajo
indefinidamente una vez que procesaron algo. Lo encontré corriendo el benchmark en
secuencia sin supervisión: un proceso llevaba 16 minutos colgado sin que lo notara.
Lo arreglé agregando `System.exit(codigo)` al final de `run()`, según el `BatchStatus` real
del `JobExecution` , dispara el shutdown hook de Spring Boot, que cierra el `TaskExecutor`
ordenadamente, y de paso corrige que antes el proceso siempre devolvía código 0 aunque el
Job fallara.

## 4. Reflexión técnica

Esta semana confirmó, con un ejemplo más contundente que los de S1/S2, algo que ya venía
viendo: un cambio que parece puramente aditivo (agregar backoff a un retry que ya existía)
puede exponer un bug que llevaba dos semanas latente. No alcanzó con que compilara después
del cambio; hizo falta correrlo contra volumen real para que apareciera.

También aprendí a no confiar en mi propio cálculo de un bug sin verificarlo: al estimar a
mano cuántas filas debería omitir cada Job (revisando el CSV con las reglas de negocio),
el número no coincidió con lo que el Job reportó al correr de verdad, no había considerado
que me faltaba tomar en cuenta, el formato de fecha en estados-cuenta. Usé el dato medido
como fuente de verdad, no mi estimación.

La migración de transacciones a particiones terminó validada dos veces: primero por diseño
(evitar el reader compartido), después por el deadlock, que confirmó que particionar no es
solo una alternativa más compleja al multi-hilo simple; es el único mecanismo de Spring
Batch que garantiza retry/skip real bajo paralelismo real.

## 5. Riesgos, decisiones abiertas y recomendaciones para futuras versiones

- **Intereses y estados-cuenta corren secuenciales, no paralelos.** Correcto para 1000
  filas (menos de 1 segundo igual), pero si esos datasets crecieran a un punto donde el
  tiempo secuencial importe, la única forma segura de paralelizarlos sería migrarlos a
  particiones, no volver a un `taskExecutor` simple sobre un Step fault-tolerant.
- **`skipLimit` depende de la proporción de datos corruptos del dataset.** Los defaults
  actuales están calibrados contra semana 3; con un dataset real distinto habría que
  recalibrarlos; quedaron configurables justo para eso.
- **Deduplicación por contenido en intereses** sigue con la misma limitación de S1/S2,
  ahora con un número concreto: descarta el 87.5% de los datos válidos en el dataset
  sintético. Un identificador único de cliente sería la solución de fondo.
- **`taskExecutor` + `faultTolerant()` fuera de particiones queda documentado como no
  seguro**, con un caso reproducible real, no solo como advertencia de la documentación de
  Spring Batch. Cualquier Step nuevo que necesite paralelismo y tolerancia a fallos a la
  vez debería particionarse desde el diseño.
- Sigue pendiente lo que anoté en S2: extender `PerformanceStepListener` a una alerta
  automática si el throughput cae bajo un umbral.

## 6. Repositorio GitHub

Código, documentación y evidencia en el repositorio:

https://github.com/Zersource/Banco-xyz-batch---S1
