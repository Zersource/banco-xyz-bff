# Banco XYZ Batch — Migración de procesos legacy a Spring Batch



	 Desarrollo Backend III — S1, S2 y S3


Actividad S1: *Analizando la arquitectura batch para procesar datos*
Actividad S2: *Configuración de Jobs y Steps: escalamiento y procesamiento paralelo*
Actividad S3: *Escalado y procesamiento paralelo avanzado (particiones, tolerancia a fallos)*


## 1. Objetivo del proyecto

Este proyecto moderniza tres procesos batch legacy (originalmente en COBOL/Shell) del
**Banco XYZ**, reescribiéndolos como Jobs de **Spring Batch**:

| Job (bean) | Alias CLI | Qué hace |
|---|---|---|
| `dailyTransactionsJob` | `transacciones` | Lee `transacciones.csv`, valida/filtra anomalías (montos negativos o cero, duplicados, fechas mal formateadas) y genera un resumen diario. |
| `monthlyInterestJob` | `intereses` | Lee `intereses.csv`, valida cada cuenta (edad, saldo, tipo) y calcula el interés mensual según el producto (ahorro/préstamo/hipoteca), actualizando el saldo final. |
| `annualStatementJob` | `estados-cuenta` | Lee `cuentas_anuales.csv`, valida cada movimiento y compila un estado de cuenta anual por cuenta para auditoría. |

Los datos de origen vienen de
[`KariVillagran/bank_legacy_data`](https://github.com/KariVillagran/bank_legacy_data), que
simula un sistema legacy con datos "sucios": montos negativos/cero, fechas en varios
formatos, saldos vacíos, edades fuera de rango, descripciones faltantes y registros
duplicados. Antes de fijar las reglas de validación revisé el dataset completo (las 3
semanas de ejemplo, no solo la primera) para no asumir errores que en realidad no
estaban ahí — el detalle de qué encontré está en la sección "Reglas de negocio aplicadas".

## 2. Estructura del proyecto

```
banco-xyz-batch/
├── pom.xml
├── docker-compose.yml                 # PostgreSQL para el perfil "postgres"
├── src/main/java/cl/duoc/bancoxyz/
│   ├── BancoXyzBatchApplication.java
│   ├── config/
│   │   ├── JobLauncherRunner.java             # selecciona y lanza el Job pedido por CLI
│   │   ├── JobCompletionNotificationListener.java
│   │   ├── BatchThreadingConfig.java          # TaskExecutor del particionado (semana 2/3)
│   │   └── BatchRetryConfig.java              # BackOffPolicy exponencial compartida (semana 3)
│   ├── common/
│   │   ├── FechaUtils.java                    # parseo flexible de fechas legacy
│   │   ├── AnomaliaAuditor.java                # audita cada anomalía en anomalia_dato
│   │   └── PerformanceStepListener.java        # métricas de duración/throughput (semana 2)
│   ├── transacciones/      # Job 1: reader/processor/writer + Job config + Partitioner (S3)
│   ├── intereses/          # Job 2: reader/processor/writer + Job config
│   └── estadocuenta/       # Job 3: reader/processor/writer + Job config
├── src/main/resources/
│   ├── application.yml                # perfiles dev (H2), postgres y semana_3 (datos de 1000 filas)
│   ├── schema-bancoxyz.sql            # tablas de negocio (H2 y PostgreSQL)
│   └── data/semana_1/, semana_3/*.csv # datos de ejemplo (copiados del repo legacy)
└── src/test/java/...                  # pruebas de integración de los 3 Jobs
```

### Arquitectura de cada Job

Los tres Jobs siguen el mismo patrón: Job → Steps → `ItemReader`/`ItemProcessor`/
`ItemWriter`. Es el "estereotipo batch" de Spring Batch, y me apoyé en él en vez de
inventar una estructura propia porque es justo lo que el framework espera y lo que
cualquiera que revise el código va a reconocer de inmediato.

1. **Step de limpieza** (`Tasklet`): vacía las tablas de salida para que el Job se pueda
   reejecutar sin duplicar nada.
2. **Step de carga** (chunk-oriented, tamaño de chunk = 20 desde semana 3 — ver la
   sección 4 para el porqué):
   - **Transacciones diarias** corre particionado (3 particiones sobre rangos de líneas
     del CSV, cada una con su propio `FlatFileItemReader` vía `@StepScope`) — es el único
     de los tres Jobs con paralelismo real. **Intereses y estados de cuenta** corren
     secuenciales desde semana 3 (el porqué está en la sección 4.2, es un hallazgo real,
     no una limitación de diseño original).
   - `FlatFileItemReader` lee el CSV fila por fila, mapeando todos los campos como
     `String` en un objeto `*Raw`. Lo hice así a propósito: evita que el reader falle
     ante un campo vacío o mal formado, y deja que sea el `ItemProcessor` quien decida
     qué hacer con cada caso.
   - `ItemProcessor` valida, corrige (cuando es razonable, por ejemplo una descripción
     vacía) o descarta (cuando el dato es irrecuperable, por ejemplo una fecha ilegible)
     cada fila. Todo descarte o corrección queda registrado en `anomalia_dato` vía
     `AnomaliaAuditor`.
   - `JdbcBatchItemWriter` inserta los registros válidos en su tabla destino.
   - El step es `faultTolerant()` con `skip` sobre `FlatFileParseException`,
     `DatoInvalidoException` y `DuplicateKeyException`, más `retry` con backoff
     exponencial sobre errores transitorios de base de datos (sección 4.3), para que una
     línea mal formada, un duplicado real o una caída momentánea de conexión no tumben
     todo el Job.
3. **Step de agregación** (`Tasklet` con SQL `GROUP BY`): genera el resumen o el estado
   de cuenta final a partir de los datos ya validados.

### Manejo de errores: omisión (skip) y reintento (retry) reales de Spring Batch

El dataset de referencia dice explícitamente que sus problemas "deberán ser gestionados
mediante políticas de reintento y omisión en Spring Batch". Por eso no resolví el manejo
de errores con lógica a mano dentro del `ItemProcessor` — usé el mecanismo nativo del
framework:

- Cuando un dato es **irrecuperable** (fecha ilegible, monto inválido, tipo desconocido),
  el `ItemProcessor` audita el detalle en `anomalia_dato` y lanza
  `DatoInvalidoException`. Cada step de carga está configurado con
  `.faultTolerant().skip(DatoInvalidoException.class).skip(FlatFileParseException.class)`,
  así que es Spring Batch quien omite esa fila y sigue con la siguiente — no un `if` que
  la descarta a mano. Un `LoggingSkipListener` deja además constancia en el log de cada
  skip efectivo, como evidencia de que el framework realmente lo gestionó.
- Cuando el dato es un **duplicado real** (misma clave de negocio que otra fila ya
  cargada), ya no lo detecta el `ItemProcessor`: lo revela el propio `INSERT` al chocar
  con un constraint `UNIQUE` de la tabla destino, y el step tiene
  `.skip(DuplicateKeyException.class)` para omitirlo igual que cualquier otro dato
  irrecuperable. Explico el porqué de este cambio (viene de la semana 1) en la sección 3.
- Cuando un dato es **corregible** (por ejemplo una descripción vacía en
  `cuentas_anuales.csv`), el `ItemProcessor` no lanza excepción: reemplaza el valor por
  uno por defecto y conserva la fila. Es una corrección, no una omisión.
- Cada step de carga tiene además `.retry(TransientDataAccessException.class).retryLimit(3)`
  para que un error transitorio de base de datos (una caída momentánea de conexión, por
  ejemplo) se reintente en vez de abortar el Job. En desarrollo local esto casi nunca se
  dispara — es una política pensada para producción, no algo que el dataset de ejemplo
  provoque a propósito.

### Reglas de negocio aplicadas (y cómo llegué a ellas)

No definí estas reglas solo mirando la muestra de la semana 1: las fue verificando contra las
tres semanas completas del dataset de referencia, para confirmar que el patrón era real
y no una coincidencia de una muestra chica.

- **Transacciones diarias**: descarto montos `<= 0`. Las fechas se interpretan en 4
  formatos (ver más abajo). **Duplicados**: se detectan por la clave de negocio
  `fecha + monto + tipo`, no por el `id` — comprobé que en el dataset real dos filas
  duplicadas traen un `id` distinto (por ejemplo `id=6` e `id=8` con idéntica
  fecha/monto/tipo); deduplicar por `id` no detectaba nada.
- **Intereses mensuales**: tasa mensual por tipo de cuenta — ahorro `0.5%`, préstamo
  `1.2%`, hipoteca `0.8%` (regla de negocio simulada, configurable en
  `InteresItemProcessor.TASAS_POR_TIPO`). Procedo a descartar cuentas con edad fuera de rango
  (1-120) o saldo vacío/negativo (saldo en cero sí es válido). **Duplicados**: los
  controlo tanto por `cuenta_id` repetido como por la clave de contenido
  `nombre + saldo + edad + tipo`, porque el duplicado real del dataset repite estos 4
  campos con un `cuenta_id` distinto (por ejemplo `cuenta_id=101` y `106`, ambos "John
  Doe,5000,30,ahorro"). Dejo anotada una limitación conocida: con datasets sintéticos
  grandes y un catálogo pequeño de nombres, esta clave de contenido puede dar falsos
  positivos entre clientes distintos que coinciden por azar; en un sistema real usaría un
  identificador único de cliente en vez de nombre+saldo+edad+tipo.
- **Estados de cuenta anuales**: descarto monto `<= 0`, igual que en los otros dos
  procesos. Evalué aceptar montos negativos como retiros/compras legítimos, pero al
  revisar el dataset completo no hay un patrón de signo consistente por tipo de
  transacción (hay depósitos negativos y retiros/compras positivos mezclados sin regla
  clara), lo que confirma que el signo negativo es ruido inyectado a propósito — el
  README del dataset de origen lo documenta como uno de los errores simulados. Una
  descripción vacía sí se corrige con un valor por defecto en vez de descartar el
  movimiento completo.
- **Fechas** (los tres procesos): soporto 4 formatos detectados en el dataset completo —
  `yyyy-MM-dd`, `yyyy/MM/dd`, `dd-MM-yyyy`, `dd/MM/yyyy`. Para los formatos de 2 dígitos
  ambiguos asumo día-mes-año (convención latinoamericana) y no mes-día-año; es una
  decisión de negocio explícita, documentada en `FechaUtils`, propia de cualquier
  migración real desde un sistema legacy sin metadatos de formato.

## 3. Semana 2 — Escalamiento y procesamiento paralelo

La actividad de la semana 2 pide procesar con chunks de tamaño 5 y 3 hilos de ejecución
paralela. Acá documento las decisiones de diseño que tomé para cumplir eso sin romper la
corrección de los tres Jobs, porque el cambio no fue trivial: subir el chunk y agregar
hilos expuso dos problemas reales que la semana 1 no tenía, y los resolví con evidencia
de ejecución, no solo leyendo la documentación de Spring Batch.

### 3.1 TaskExecutor simple, no Partitioning

Configuré un `ThreadPoolTaskExecutor` de 3 hilos fijos (`corePoolSize`/`maxPoolSize` = 3,
`queueCapacity` = 0) compartido por los tres steps de carga, más `throttleLimit(3)` en
cada uno. El `queueCapacity` en 0 es a propósito: como `throttleLimit(3)` ya limita a 3
chunks concurrentes como máximo, no necesito una cola de espera adicional — simplifica lo
que hay que medir con el log de rendimiento, porque no quedan chunks "atascados"
esperando turno.

Evalué también la alternativa de `Partitioner`/`PartitionHandler` (particionamiento real
de Spring Batch), pero la descarté: mis datasets tienen 8-10 filas por CSV, y particionar
un volumen así agrega coordinación sin ningún beneficio real de throughput — sería
complejidad que la actividad no pide (la pauta habla de "chunks y multithreading", no de
particiones). El multithreaded-step con `TaskExecutor` + `throttleLimit` es exactamente
el mecanismo estándar que Spring Batch documenta para este escenario.

### 3.2 Por qué el reader necesita `SynchronizedItemStreamReader`

`FlatFileItemReader` no es thread-safe: su estado interno de posición de lectura no
soporta llamadas concurrentes a `read()`. Con `throttleLimit(3)` activo, varios hilos
llaman a `read()` en paralelo sobre el mismo reader, así que lo envolví en
`SynchronizedItemStreamReader`, que sincroniza el acceso. Sin este cambio el riesgo era
saltarse o duplicar filas de forma intermitente y difícil de reproducir — lo agregué de
forma preventiva, documentado como decisión de diseño y no como bug encontrado, porque
una condición de carrera puede perfectamente no manifestarse en una corrida de prueba con
un dataset de solo 8-10 filas, aunque el problema exista igual.

### 3.3 Deduplicación: de un `Set` en memoria a un constraint `UNIQUE`

En la semana 1, `TransaccionItemProcessor` e `InteresItemProcessor` detectaban duplicados
con un `Set` en memoria, poblado en `ItemWriteListener.afterWrite()` y dependiente de
`chunk(1, ...)` para ser confiable — con un chunk mayor, todos los `process()` de un
chunk corren antes que el primer `write()`, así que dos duplicados reales en el mismo
chunk pasaban ambos como válidos. Subir a `chunk(5)` con 3 hilos en paralelo rompe ese
diseño por completo: el orden de llegada de `afterWrite()` entre chunks concurrentes deja
de ser determinista, así que ya no hay un "orden de ejecución" confiable sobre el cual
basar la detección.

Reemplacé la deduplicación en memoria por un constraint `UNIQUE` en la base de datos:
`uk_transaccion_clave_negocio` sobre (fecha, monto, tipo) en `transaccion_validada`, y
`uk_interes_clave_contenido` sobre (nombre, saldo_inicial, edad, tipo) en
`cuenta_interes_mensual`. El `INSERT` duplicado lanza `DuplicateKeyException`, que
configuré como excepción `skip` en el step correspondiente. Esto funciona sea cual sea el
chunk size o el número de hilos, porque la garantía de unicidad la da la base de datos y
no el orden de ejecución en memoria — es un diseño más robusto que el de la semana 1, no
un parche.

Esto trae una consecuencia visible en los números que reporto en la sección 6: como los
duplicados ahora se detectan a nivel de escritura y no dentro del `ItemProcessor`, ya no
generan una fila en `anomalia_dato` — se detectan y omiten igual, pero la auditoría
central queda reservada a los datos que el `ItemProcessor` sí puede evaluar (fecha
ilegible, monto inválido, tipo desconocido). Lo dejo explícito para que no se lea como
una regresión: el dato sigue sin llegar a la tabla de negocio, solo cambió dónde queda
registrado el motivo.

### 3.4 Auditoría en transacción independiente (`REQUIRES_NEW`)

Al correr el test de integración con `chunk(5)` encontré un problema que no era visible
leyendo el código: cuando un chunk físico contenía a la vez un dato inválido (auditado y
marcado `noRollback`) y un duplicado que fallaba al escribir (`DuplicateKeyException`,
sin `noRollback`), Spring Batch revertía toda la transacción del chunk al ocurrir la
segunda excepción — incluyendo el `INSERT` de auditoría ya hecho para el primer dato,
porque `noRollback` solo protege la transacción si esa es la única excepción que
participa en el fallo. El resultado: se perdía una fila de `anomalia_dato` que en la
semana 1 sí quedaba registrada. Lo confirmé comparando el conteo esperado contra el real
en `mvn test`, no lo intuí leyendo el código.

Marqué `AnomaliaAuditor.registrar()` con `@Transactional(propagation =
Propagation.REQUIRES_NEW)`, de modo que cada auditoría se compromete en su propia
transacción, independiente de lo que le pase después al resto del chunk. Esto también
reduce la dependencia del mecanismo de re-escaneo item-por-item de `faultTolerant()`, que
el propio Spring Batch advierte como no del todo confiable al combinarse con un
`taskExecutor` multihilo y un reader `ItemStream` — el warning
`"Asynchronous TaskExecutor detected with ItemStream reader"` aparece en el log de
arranque de cada step.

### 3.5 Logs de rendimiento

Agregué `PerformanceStepListener`, un `StepExecutionListener` reusado en los tres steps
de carga, que registra en el log la duración real de cada ejecución
(`endTime - startTime`), la cantidad de ítems leídos/escritos/omitidos por tipo (lectura,
proceso, escritura) y el throughput en ítems por segundo. No mantiene estado propio: toda
la información sale de `StepExecution`, que Spring Batch trackea de forma segura bajo
concurrencia, así que no reintroduce el problema descrito en 3.3.

## 4. Semana 3 — Escalado avanzado, tolerancia a fallos y benchmark

La actividad pide decidir entre profundizar el multi-hilo de S2 o migrar a particiones
reales, comparando parámetros para encontrar la configuración óptima, más políticas de
reintento explícitas. El plan inicial era correr ambos enfoques en paralelo; terminó en
una arquitectura mixta, decidida por un hallazgo real durante el benchmark, no por diseño
de antemano — el detalle completo está en `PROPUESTA_TECNICA.md`, acá dejo cómo quedó
configurado.

### 4.1 Transacciones diarias: de multi-hilo a particiones reales

`TransaccionRangoPartitioner` divide `transacciones.csv` en rangos de líneas — uno por
partición — dejando `lineasASaltar`/`cantidadItems` en el `ExecutionContext` de cada una.
`transaccionParticionadoReader` (`@StepScope`) arma su propio `FlatFileItemReader` acotado
a ese rango, así que cada partición tiene su reader independiente y ya no necesito
`SynchronizedItemStreamReader`. `TaskExecutorPartitionHandler` reparte las particiones
sobre el mismo pool de `BatchThreadingConfig`.

### 4.2 Intereses y estados de cuenta: por qué quedaron secuenciales

Mantuve el multi-hilo de S2 en estos dos Jobs hasta que, al agregar el `BackOffPolicy`
(sección 4.3), el test de intereses quedó colgado contra el dataset de 1000 filas de
semana 3. No fue el volumen: fue que combinar `taskExecutor` con `.faultTolerant()` en un
Step no particionado no es una combinación que Spring Batch garantice segura — el manejo
interno de reintentos dentro de un chunk no está pensado para que varios hilos lo toquen a
la vez. Con el CSV chico de S1/S2 nunca se disparó; con volumen real y el delay del backoff
exponiendo el timing exacto, sí. Detalle del diagnóstico en `PROPUESTA_TECNICA.md`,
sección 3.

Lo arreglé quitando `taskExecutor`/`throttleLimit` de ambos steps — quedan secuenciales,
con retry/skip/backoff intactos. Es el mismo principio que motivó particionar
transacciones: paralelismo real + tolerancia a fallos solo es seguro combinado con
particiones.

### 4.3 Políticas de reintento con backoff explícito

`BatchRetryConfig` agrega un `ExponentialBackOffPolicy` compartido por los tres Jobs: 200
ms antes del primer reintento, duplicando hasta un tope de 2000 ms. Antes (S2), un
reintento se disparaba al instante — sin espera, reintentar contra un error transitorio de
conexión cae en el mismo problema que lo originó.

### 4.4 Parámetros configurables y benchmark de escalado

Los parámetros de escalado quedaron como propiedad, con el default calibrado por el
benchmark:

| Propiedad | Aplica a | Default |
|---|---|---|
| `app.batch.thread-pool-size` | Particionado de transacciones | 3 |
| `app.batch.transacciones.grid-size` | Transacciones (# particiones) | 3 |
| `app.batch.transacciones.chunk-size` | Transacciones | 20 |
| `app.batch.transacciones.skip-limit` | Transacciones | 700 |
| `app.batch.intereses.chunk-size` | Intereses | 20 |
| `app.batch.intereses.skip-limit` | Intereses | 1000 |
| `app.batch.estados-cuenta.chunk-size` | Estados de cuenta | 20 |
| `app.batch.estados-cuenta.skip-limit` | Estados de cuenta | 500 |

Con datos de semana 3 (perfil `semana_3`, 1000 filas por CSV) corrí 10 combinaciones
midiendo con `PerformanceStepListener` (tabla completa en `PROPUESTA_TECNICA.md`, sección
2.4). Resumen:

- **Transacciones**: entre 3 y 5 particiones el tiempo mejora poco. Subir `chunk-size` de
  5 a 20 con 3 particiones (405 ms) rindió igual o mejor que subir a 5 particiones
  (428 ms) — de ahí `grid-size=3, chunk-size=20`.
- **Intereses/estados-cuenta**: la ganancia se concentra entre chunk 5 y 20; de 20 a 50 ya
  es marginal — de ahí `chunk-size=20` en ambos.

El `skipLimit` subió tanto porque el CSV de semana 3 trae datos corruptos por diseño a
gran escala: medí 599 filas omitidas en transacciones, 950 en intereses (el catálogo
sintético de 8 nombres distintos hace que el constraint `UNIQUE` descarte como duplicado el
87.5% de lo que sí pasa las reglas de negocio) y 386 en estados-cuenta. Con datos de este
volumen, 200 nunca iba a alcanzar.

### 4.5 Ejecutar contra semana 3 y variar parámetros

```bash
java -jar target/banco-xyz-batch-1.0.0.jar transacciones --spring.profiles.active=dev,semana_3

# variar parámetros sin recompilar, ej. más particiones:
java -jar target/banco-xyz-batch-1.0.0.jar transacciones --spring.profiles.active=dev,semana_3 \
  --app.batch.transacciones.grid-size=5 --app.batch.thread-pool-size=5
```

**Importante**: `thread-pool-size` debe ser mayor o igual que `grid-size` — el pool no
tiene cola de espera, así que si hay más particiones que hilos disponibles, las que sobran
se rechazan con `TaskRejectedException`.

## 5. Cómo ejecutar

### Requisitos

- JDK 21
- Maven 3.9+ (o el wrapper `./mvnw` si lo agregas con `mvn -N wrapper:wrapper`)
- Docker (opcional, solo para el perfil `postgres`)

### Opción A — Perfil `dev` (recomendado para evidencia rápida, usa H2 embebido)

No se requiere ninguna base de datos externa.

```bash
mvn clean package
java -jar target/banco-xyz-batch-1.0.0.jar transacciones
java -jar target/banco-xyz-batch-1.0.0.jar intereses
java -jar target/banco-xyz-batch-1.0.0.jar estados-cuenta

# o los tres en secuencia:
java -jar target/banco-xyz-batch-1.0.0.jar todos
```

La base H2 queda persistida en `./data/bancoxyz.mv.db`. Puedes inspeccionarla con la
consola web de H2 (`http://localhost:8080/h2-console`, si dejas la app corriendo) o con
cualquier cliente JDBC apuntando a `jdbc:h2:file:./data/bancoxyz`.

### Opción B — Perfil `postgres` (para simular producción)

```bash
docker compose up -d postgres
mvn clean package
java -jar target/banco-xyz-batch-1.0.0.jar todos --spring.profiles.active=postgres
```

### Ejecutar contra otras semanas de datos

Los CSV de `semana_1` (8-10 filas) y `semana_3` (1000 filas, ver sección 4.5) ya vienen
incluidos en el proyecto. Para usar `semana_2` u otra semana del repositorio legacy que no
esté empaquetada, sobreescribe las rutas por propiedad:

```bash
java -jar target/banco-xyz-batch-1.0.0.jar transacciones \
  --app.csv.transacciones=file:/ruta/a/bank_legacy_data/data/semana_2/transacciones.csv
```

### Pruebas automatizadas

```bash
mvn test
```

Las pruebas (`BancoXyzBatchApplicationTests`) ejecutan los tres Jobs de punta a punta
contra los CSV de ejemplo sobre una base H2 en memoria, y verifican que cada Job termina
`COMPLETED` y que las anomalías del dataset legacy quedan auditadas en vez de romper el
proceso.

## 6. Evidencia de ejecución

Al correr cualquier Job se ve en consola:

- El listener `JobCompletionNotificationListener` mostrando el estado final y la cantidad de
  anomalías auditadas.
- Cada anomalía individual detectada (`AnomaliaAuditor`), con el detalle y el dato crudo
  original — sirve como evidencia de la gestión de errores exigida por la actividad.

Para la entrega, guardo la salida de consola de los tres Jobs
(`mvn clean test | tee evidencia_ejecucion_s2.log` para S1/S2, con el dataset chico de
`semana_1`). Para semana 3, la evidencia relevante es la del benchmark contra `semana_3`
(sección 4.4-4.5) — guardo la salida de cada corrida con
`java -jar ... --spring.profiles.active=dev,semana_3 [...] | tee evidencia_ejecucion_s3.log`,
ya que con 1000 filas por CSV el log de `PerformanceStepListener` es la evidencia que
realmente importa (duración y throughput reales), más que el detalle fila por fila de
`anomalia_dato` que sí tenía sentido revisar a mano con los 8-10 registros de `semana_1`.

> **Nota:** la tabla `anomalia_dato` es un log de auditoría acumulativo — a diferencia de
> las tablas de negocio (que cada Job limpia al inicio vía su step de limpieza),
> `anomalia_dato` NO se limpia entre corridas, por diseño: un log de auditoría real no
> debería perder historial cada vez que corre el batch. Si corres los Jobs más de una vez
> contra el mismo `./data/bancoxyz.mv.db`, el conteo reportado por
> `JobCompletionNotificationListener` va a ser acumulado, no el de la corrida actual.
> Para reproducir los conteos exactos documentados acá (2 anomalías en transacciones, 0
> en intereses, 3 en estados de cuenta — ver sección 3.3 sobre por qué los duplicados ya
> no generan fila en `anomalia_dato` desde la semana 2), borra la carpeta `./data/` antes
> de correr, o usa `mvn test` (que siempre corre contra H2 en memoria, limpio en cada
> ejecución).


### Nota: ¿por qué no `controller → service → repository → model`?

Este proyecto no sigue el patrón de capas de una app CRUD/REST. En un Job batch la unidad
de trabajo es distinta: Job → Step → Reader/Processor/Writer es el patrón equivalente del
dominio batch, definido por el propio framework de Spring Batch, no una simplificación
mía por descuido. `ItemProcessor` cumple el rol que en una app REST cumpliría el
`service` (la lógica de negocio/validación), y `JdbcBatchItemWriter`/`ItemReader`
cumplen el rol del `repository` (acceso a datos), pero organizados según el ciclo de vida
de un Job batch (lectura → procesamiento → escritura en chunks), no según el ciclo
petición-respuesta HTTP de un controller REST.

### Nota: ¿por qué tests de integración (`@SpringBootTest`) y no Mockito?

En Backend II los tests unitarios con Mockito (mockeando interfaces directo, sin
`@InjectMocks`) tenían sentido porque probaba lógica de negocio aislada en clases
`service`. Acá el objeto bajo prueba es el Step completo (reader + processor + writer +
políticas de skip/retry actuando en conjunto sobre un chunk), que es exactamente el
comportamiento que hay que validar: que un CSV con datos sucios efectivamente produzca
las filas válidas esperadas, que las anomalías queden auditadas, y que el Job termine
`COMPLETED` en vez de fallar. El llevar a prueba Mockear el `ItemReader` o el `ItemProcessor` 
por separado no prueba esa integración — probaría cada pieza aislada, mientras que lo importante en
Spring Batch viene siendo el cómo cooperan las piezas dentro de un chunk fault-tolerant. Por eso las
pruebas de este proyecto (`BancoXyzBatchApplicationTests`) son de integración contra una
base H2 real, con conteos exactos que tuve que verificar contra el dataset.

### Nota: bugs reales encontrados en la semana 1 (no visibles leyendo el código en frío)

Este proyecto lo tuve que pasar por tres rondas de corrección después de la implementación inicial,
todas encontradas solo al compilar y correr de verdad, no al revisar el código:

1. **Deduplicación rota bajo el mecanismo de skip de Spring Batch.** El Set en memoria
   usado para detectar duplicados por clave de negocio se mutaba dentro de `process()`.
   Cuando un chunk con más de 1 ítem fallaba por un duplicado real, Spring Batch
   revertía la transacción y reprocesaba el chunk ítem por ítem para aislar cuál falló —
   pero el Set (al no ser parte de la transacción JDBC) conservaba entradas del intento
   fallido, marcando ítems válidos como "duplicados de sí mismos". Lo arreglé con
   `chunk(1, ...)` en los steps de carga de `transacciones` e `intereses`, más los
   processors implementando `ItemWriteListener`, moviendo la escritura del Set a
   `afterWrite()` (solo tras confirmar que Spring Batch efectivamente persistió el
   ítem). Los dos cambios eran necesarios juntos: `chunk(1)` sin diferir la escritura
   del Set no evita que el propio intento dañe el Set; diferir la escritura sin
   `chunk(1)` deja un punto ciego donde dos duplicados reales en el mismo chunk pasan
   ambos como válidos. Lo verifiqué leyendo el código fuente real de
   `FaultTolerantChunkProcessor.transform()`, no lo asumí.

2. **La auditoría de anomalías se revertía junto con el chunk.** `DatoInvalidoException`
   estaba en `.skip(...)` pero no en `.noRollback(...)`, así que Spring Batch revertía
   toda la transacción del chunk al lanzarla — incluyendo el `INSERT` de
   `AnomaliaAuditor`, que corre dentro de esa misma transacción. El log mostraba las
   anomalías (SLF4J no es transaccional) pero la tabla `anomalia_dato` quedaba vacía.
   Lo arreglé agregando `.noRollback(DatoInvalidoException.class)` junto a
   `.skip(DatoInvalidoException.class)` en los tres steps de carga (no en
   `FlatFileParseException`, que ocurre en la lectura, antes de cualquier insert de
   auditoría).

3. **`anomalia_dato` es acumulativa entre corridas**, a diferencia de las tablas de
   negocio que cada Job limpia en su step de limpieza — ver la nota en la sección 6.

### Nota: bugs reales encontrados en la semana 2 al subir a chunk(5) + 3 hilos

Estos dos solo aparecieron al compilar y correr de verdad contra `mvn test`, no al
revisar el diseño en frío — igual que los de la semana 1:

4. **`.listener(StepExecutionListener)` después de `.retry()` rompe la compilación.**
   `FaultTolerantStepBuilder` solo sobreescribe `.listener(...)` para
   `SkipListener`/`ChunkListener`/`RetryListener` (devolviendo el mismo builder
   fault-tolerant); para cualquier otro tipo de listener (como `PerformanceStepListener`,
   que solo implementa `StepExecutionListener`) la llamada cae en el `.listener(Object)`
   genérico y devuelve `SimpleStepBuilder`, que ya no tiene `.retry()`. Lo arreglé
   moviendo `.listener(performanceListener)` al final de la cadena, después de
   `.retry()`/`.retryLimit()`, igual que ya lo había resuelto para el caso análogo de la
   semana 1 con `ItemWriteListener`.

5. **Pérdida de una fila de auditoría por interacción entre `noRollback` y
   `DuplicateKeyException`** — el detalle completo está en la sección 3.4. Lo arreglé con
   `@Transactional(propagation = Propagation.REQUIRES_NEW)` en
   `AnomaliaAuditor.registrar()`.

La moraleja que vengo aplicando en este proyecto (y que ya venía de Backend II) y que me 
ha servido para mi proyecto personal es: ningún fix se da por bueno solo porque compila o 
"se ve correcto" ; hay que preocuparse de verificarlo cada vez contra el resultado real de `mvn test` 
(H2 en memoria, limpio) y contra el código fuente real de Spring Batch cuando 
el comportamiento no es obvio.

### Nota: bugs reales encontrados en la semana 3

Otra vez, ninguno visible en el código en frío. Diagnóstico completo de cada uno en
`PROPUESTA_TECNICA.md`, sección 3; acá el resumen al estilo de los anteriores:

6. **Deadlock combinando `taskExecutor` con `faultTolerant()` fuera de particiones.** Al
   agregar el backoff (4.3), el test de intereses quedó colgado — no lento, colgado. Lo
   arreglé quitando `taskExecutor`/`throttleLimit` de intereses y estados-cuenta (4.2).

7. **`skipLimit(200)` insuficiente para datos reales de volumen.** 599/950/386 filas
   omitidas en transacciones/intereses/estados-cuenta, muy por encima de 200. Lo arreglé
   con `skipLimit` configurable por Job, calibrado contra esos conteos reales (4.4).

8. **La JVM no cerraba sola al terminar un Job vía CLI.** `JobLauncherRunner` nunca
   llamaba `System.exit()`, y los hilos del pool de `BatchThreadingConfig` quedan vivos
   esperando trabajo indefinidamente. Lo encontré con un proceso 16 minutos colgado
   corriendo el benchmark sin supervisión. Lo arreglé agregando `System.exit(codigo)` al
   final de `run()`, según el `BatchStatus` real — de paso corrige que antes el proceso
   siempre devolvía código 0 aunque el Job fallara.

Misma moraleja de S1/S2, con un ejemplo más contundente: un cambio aditivo (agregar
backoff a un retry que ya existía) expuso un bug latente hace dos semanas. No alcanzó con
que compilara — hizo falta correrlo contra volumen real.

## 7. Notas de diseño / supuestos y limitaciones

- Inyección de dependencias por campo (`@Autowired`), no por constructor, en todas
  las clases `@Component` (`AnomaliaAuditor`, `JobCompletionNotificationListener`,
  `JobLauncherRunner`, los 3 `ItemProcessor`), consistente con el estilo que uso desde
  Backend II. Los `@Bean` de los `*JobConfig` siguen recibiendo dependencias por
  parámetro de método, porque así es como funciona el Java-config de Spring — no aplica
  el mismo dilema campo-vs-constructor de una clase `@Component`.

- Las tasas de interés son una regla de negocio simulada razonable, no un dato provisto
  por el enunciado; ajústalas si tu propuesta técnica define otros valores.

- Elegí PostgreSQL como base "real" (vía `docker-compose.yml`) para cumplir el requisito
  de usar PostgreSQL, MySQL u Oracle, y H2 como base embebida para desarrollo y pruebas
  sin dependencias externas.

- La deduplicación por contenido (`intereses`) puede dar falsos positivos en datasets
  sintéticos grandes con pocos valores distintos posibles (ver la limitación detallada en
  la sección de reglas de negocio y en el Javadoc de `InteresItemProcessor`). Lo
  documento ya que viene siendo, una limitación real de usar
  nombre+saldo+edad+tipo como proxy de identidad, no un bug. El benchmark de semana 3
  (sección 4.4) lo confirma con un número concreto: descarta el 87.5% de los datos que sí
  pasan las reglas de negocio, porque el catálogo sintético solo tiene 8 nombres distintos.

- **Criterio de diseño permanente desde semana 3**: paralelismo real combinado con
  `faultTolerant()` solo es seguro en Spring Batch si el Step está particionado — llegó a
  producir un deadlock real en este proyecto (ver bugs de la semana 3). Cualquier Step
  nuevo que necesite ambas cosas a la vez debería particionarse desde el diseño.