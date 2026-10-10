package cl.duoc.bancoxyz.transacciones;

import cl.duoc.bancoxyz.common.DatoInvalidoException;
import cl.duoc.bancoxyz.common.LoggingSkipListener;
import cl.duoc.bancoxyz.common.PerformanceStepListener;
import cl.duoc.bancoxyz.config.JobCompletionNotificationListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Job "Reporte de Transacciones Diarias".
 * <p>
 * Migra el proceso legacy que detectaba anomalias en las transacciones
 * diarias y generaba un resumen. Se compone de tres steps:
 * <ol>
 *     <li>{@code limpiarTransaccionesStep}: deja las tablas de salida
 *     vacias para que el Job sea reejecutable de forma idempotente.</li>
 *     <li>{@code particionarTransaccionesStep}: step maestro que divide
 *     {@code transacciones.csv} en particiones (ver
 *     {@link TransaccionRangoPartitioner}) y las ejecuta en paralelo, cada
 *     una corriendo {@code cargarTransaccionesWorkerStep} sobre su propio
 *     rango de lineas.</li>
 *     <li>{@code generarResumenDiarioStep}: agrega los datos validos por
 *     fecha en la tabla {@code resumen_transacciones_diarias}, una vez que
 *     todas las particiones terminaron.</li>
 * </ol>
 * <p>
 * <b>Cambio de arquitectura en semana 3 (de multi-thread a particiones):</b>
 * en semana 2 la concurrencia se lograba con {@code chunk(5)} + un
 * {@code TaskExecutor} de 3 hilos + {@code throttleLimit(3)} sobre un unico
 * Step, donde los 3 hilos competian por leer del mismo
 * {@code FlatFileItemReader} (de ahi el {@code SynchronizedItemStreamReader}
 * que sincronizaba ese acceso). Con particiones, cada hilo ejecuta una
 * instancia independiente del Step (con su propio reader, {@code @StepScope},
 * sobre su propio rango de lineas), asi que ya no hay un reader compartido
 * que sincronizar. Se elige particionar este Job en particular porque es el
 * que mas se beneficia de ese cambio: los otros dos (intereses, estados de
 * cuenta) se mantienen con el modelo multi-thread de semana 2, reforzado con
 * el backoff exponencial de {@link cl.duoc.bancoxyz.config.BatchRetryConfig},
 * para poder comparar ambos enfoques con datos reales sobre el mismo
 * dataset (ver PROPUESTA_TECNICA.md, seccion de comparacion de parametros).
 */
@Configuration
public class TransaccionesDiariasJobConfig {

    @Value("${app.csv.transacciones:classpath:data/semana_1/transacciones.csv}")
    private Resource transaccionesCsv;

    /**
     * Semana 3: default subido de 5 a 20 tras el benchmark de escalado
     * (ver PROPUESTA_TECNICA.md) — con grid-size=3/pool=3, chunk=20 dio el
     * mejor tiempo medido de las 4 combinaciones probadas (405 ms vs
     * 473 ms en chunk=5), incluso mejor que subir a 5 particiones/5 hilos
     * (428 ms). Menos hilos, igual o mejor rendimiento.
     */
    @Value("${app.batch.transacciones.chunk-size:20}")
    private int chunkSize;

    @Value("${app.batch.transacciones.grid-size:3}")
    private int gridSize;

    /**
     * Semana 3: el skipLimit(200) fijo de semana 2 alcanzaba de sobra para
     * los 1-2 datos corruptos de los CSV de S1/S2. El CSV real de semana 3
     * trae 571 de 1000 filas invalidas por diseno (57.1%, medido
     * directamente sobre data_referencia/semana_3/transacciones.csv:
     * tipo no reconocido, monto no numerico/negativo/cero, mas ~26
     * duplicados por (fecha,monto,tipo)). No es un ajuste "para que pase el
     * benchmark": con datos reales de este volumen, 200 es insuficiente
     * para cualquier corrida. 700 deja margen sobre las ~597 filas
     * problematicas esperadas.
     */
    @Value("${app.batch.transacciones.skip-limit:700}")
    private int skipLimit;

    /**
     * Reader por particion. {@code @StepScope} hace que Spring cree una
     * instancia nueva por cada ejecucion de {@code cargarTransaccionesWorkerStep}
     * (una por particion), cada una leyendo las claves que
     * {@link TransaccionRangoPartitioner} dejo en su propio
     * {@code stepExecutionContext}. Al ser instancias independientes, no hay
     * estado compartido entre particiones y no hace falta sincronizar nada.
     */
    @Bean
    @StepScope
    public FlatFileItemReader<TransaccionRaw> transaccionParticionadoReader(
            @Value("#{stepExecutionContext['" + TransaccionRangoPartitioner.CLAVE_LINEAS_A_SALTAR + "']}") int lineasASaltar,
            @Value("#{stepExecutionContext['" + TransaccionRangoPartitioner.CLAVE_CANTIDAD_ITEMS + "']}") int cantidadItems) {
        FlatFileItemReader<TransaccionRaw> reader = new FlatFileItemReaderBuilder<TransaccionRaw>()
                .name("transaccionItemReader")
                .resource(transaccionesCsv)
                .linesToSkip(lineasASaltar) // encabezado + particiones anteriores
                .delimited()
                .names("id", "fecha", "monto", "tipo")
                .targetType(TransaccionRaw.class)
                .build();
        reader.setMaxItemCount(cantidadItems);
        return reader;
    }

    @Bean
    public JdbcBatchItemWriter<TransaccionValidada> transaccionItemWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<TransaccionValidada>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO transaccion_validada (id, fecha, monto, tipo)
                        VALUES (:id, :fecha, :monto, :tipo)
                        """)
                .beanMapped()
                .build();
    }

    @Bean
    public Step limpiarTransaccionesStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          JdbcTemplate jdbcTemplate) {
        return new StepBuilder("limpiarTransaccionesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    jdbcTemplate.update("DELETE FROM transaccion_validada");
                    jdbcTemplate.update("DELETE FROM resumen_transacciones_diarias");
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public LoggingSkipListener<TransaccionRaw, TransaccionValidada> transaccionSkipListener() {
        return new LoggingSkipListener<>("dailyTransactionsJob");
    }

    @Bean
    public PerformanceStepListener transaccionPerformanceListener() {
        return new PerformanceStepListener();
    }

    /**
     * Step "minion": el trabajo real de lectura/proceso/escritura, ejecutado
     * una vez por particion. No lleva {@code taskExecutor} ni
     * {@code throttleLimit} propio — el paralelismo entre particiones lo
     * administra {@code transaccionPartitionHandler}, no este step.
     */
    @Bean
    public Step cargarTransaccionesWorkerStep(JobRepository jobRepository,
                                               PlatformTransactionManager transactionManager,
                                               FlatFileItemReader<TransaccionRaw> transaccionParticionadoReader,
                                               TransaccionItemProcessor transaccionItemProcessor,
                                               JdbcBatchItemWriter<TransaccionValidada> transaccionItemWriter,
                                               LoggingSkipListener<TransaccionRaw, TransaccionValidada> transaccionSkipListener,
                                               PerformanceStepListener transaccionPerformanceListener,
                                               BackOffPolicy transientErrorBackOffPolicy) {
        return new StepBuilder("cargarTransaccionesWorkerStep", jobRepository)
                .<TransaccionRaw, TransaccionValidada>chunk(chunkSize, transactionManager)
                .reader(transaccionParticionadoReader)
                .processor(transaccionItemProcessor)
                .writer(transaccionItemWriter)
                .faultTolerant()
                // Politica de OMISION: filas con datos irrecuperables (fecha ilegible,
                // monto invalido, tipo desconocido) o mal formadas en el CSV, mas
                // duplicados detectados por el constraint UNIQUE al escribir. Esto no
                // cambia con particiones: cada particion sigue las mismas reglas.
                .skip(DatoInvalidoException.class)
                .skip(FlatFileParseException.class)
                .skip(DuplicateKeyException.class)
                // Sin noRollback, el rollbackClassifier por defecto revierte la
                // transaccion del chunk aunque la excepcion sea skippable, lo que
                // deshace tambien el INSERT que AnomaliaAuditor ya hizo en
                // anomalia_dato antes de que TransaccionItemProcessor lanzara
                // DatoInvalidoException. No aplica a FlatFileParseException: esa
                // ocurre en la lectura, antes de que exista algo que auditar. No
                // aplica a DuplicateKeyException: no hay insert previo en
                // anomalia_dato para ese caso (el processor ya no detecta
                // duplicados, solo el INSERT fallido lo revela).
                .noRollback(DatoInvalidoException.class)
                .skipLimit(skipLimit)
                .listener(transaccionSkipListener)
                // Politica de REINTENTO: errores transitorios de base de datos (ej. una
                // caida momentanea de conexion) no deben botar la particion completa.
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                // Backoff exponencial entre reintentos (ver javadoc de BatchRetryConfig).
                .backOffPolicy(transientErrorBackOffPolicy)
                // IMPORTANTE: ver el comentario equivalente en los otros *JobConfig —
                // el listener de StepExecutionListener debe ir al final, despues de
                // .retry()/.retryLimit()/.backOffPolicy(), o el compilador pierde esos
                // metodos (FaultTolerantStepBuilder solo preserva el tipo fault-tolerant
                // para SkipListener/ChunkListener/RetryListener).
                .listener(transaccionPerformanceListener)
                .build();
    }

    /**
     * Reparte {@code cargarTransaccionesWorkerStep} entre 3 particiones,
     * ejecutadas en paralelo sobre {@code batchTaskExecutor} (el mismo pool
     * de 3 hilos usado en semana 2, ver {@code BatchThreadingConfig}). El
     * numero de particiones se lee de {@code app.batch.transacciones.grid-size}
     * (default 3, para poder comparar directamente contra el modelo
     * multi-thread de 3 hilos de los otros Jobs); el bloque de benchmarking
     * evalua si otro valor rinde mejor sin necesidad de recompilar.
     */
    @Bean
    public PartitionHandler transaccionPartitionHandler(TaskExecutor batchTaskExecutor,
                                                          Step cargarTransaccionesWorkerStep) {
        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setTaskExecutor(batchTaskExecutor);
        partitionHandler.setStep(cargarTransaccionesWorkerStep);
        partitionHandler.setGridSize(gridSize);
        return partitionHandler;
    }

    /**
     * Step maestro (master): no procesa items, solo coordina el particionado
     * y espera a que todas las particiones de {@code cargarTransaccionesWorkerStep}
     * terminen antes de continuar con {@code generarResumenDiarioStep}.
     */
    @Bean
    public Step particionarTransaccionesStep(JobRepository jobRepository,
                                              TransaccionRangoPartitioner transaccionRangoPartitioner,
                                              PartitionHandler transaccionPartitionHandler) {
        return new StepBuilder("particionarTransaccionesStep", jobRepository)
                .partitioner("cargarTransaccionesWorkerStep", transaccionRangoPartitioner)
                .partitionHandler(transaccionPartitionHandler)
                .build();
    }

    @Bean
    public Step generarResumenDiarioStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          JdbcTemplate jdbcTemplate) {
        return new StepBuilder("generarResumenDiarioStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    jdbcTemplate.update("""
                            INSERT INTO resumen_transacciones_diarias
                                (fecha, total_creditos, total_debitos, cantidad_transacciones, monto_total)
                            SELECT
                                fecha,
                                COALESCE(SUM(CASE WHEN tipo = 'credito' THEN monto ELSE 0 END), 0),
                                COALESCE(SUM(CASE WHEN tipo = 'debito' THEN monto ELSE 0 END), 0),
                                COUNT(*),
                                COALESCE(SUM(monto), 0)
                            FROM transaccion_validada
                            GROUP BY fecha
                            """);
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Job dailyTransactionsJob(JobRepository jobRepository,
                                     JobCompletionNotificationListener listener,
                                     Step limpiarTransaccionesStep,
                                     Step particionarTransaccionesStep,
                                     Step generarResumenDiarioStep) {
        return new JobBuilder("dailyTransactionsJob", jobRepository)
                .listener(listener)
                .start(limpiarTransaccionesStep)
                .next(particionarTransaccionesStep)
                .next(generarResumenDiarioStep)
                .build();
    }
}
