package cl.duoc.bancoxyz.estadocuenta;

import cl.duoc.bancoxyz.common.DatoInvalidoException;
import cl.duoc.bancoxyz.common.LoggingSkipListener;
import cl.duoc.bancoxyz.common.PerformanceStepListener;
import cl.duoc.bancoxyz.config.JobCompletionNotificationListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Job "Generacion de Estados de Cuenta Anuales".
 * <p>
 * Migra el proceso legacy que compilaba los datos anuales de cada cuenta
 * para generar un informe detallado destinado a auditorias. Consta de tres
 * steps: limpieza, carga/validacion de movimientos y agregacion anual por
 * cuenta.
 * <p>
 * <b>Semana 3 — Step secuencial, no multi-hilo:</b> {@code cargarMovimientosStep}
 * tenia la misma combinacion que {@code calcularInteresesStep}
 * (multi-hilo + fault-tolerant) que produjo un deadlock real al correr
 * {@code calcularInteresesStep} contra el dataset de 1000 filas de semana 3
 * (ver el javadoc de {@code InteresesMensualesJobConfig} para el detalle del
 * hallazgo). Se aplica preventivamente el mismo ajuste aca, sin esperar a
 * que el mismo bug aparezca al correr este Job con volumen real.
 */
@Configuration
public class EstadosCuentaAnualesJobConfig {

    @Value("${app.csv.cuentas-anuales:classpath:data/semana_1/cuentas_anuales.csv}")
    private Resource cuentasAnualesCsv;

    /**
     * Semana 3: default subido de 5 a 20 tras el benchmark de escalado (ver
     * PROPUESTA_TECNICA.md) — mismo patron que en los otros dos Jobs: la
     * ganancia se concentra entre chunk 5 y 20 (665 ms -> 516 ms); subir a
     * 50 solo mejora otros 47 ms.
     */
    @Value("${app.batch.estados-cuenta.chunk-size:20}")
    private int chunkSize;

    /**
     * Semana 3: mismo motivo que en TransaccionesDiariasJobConfig. Medido
     * corriendo el Job real contra data_referencia/semana_3/cuentas_anuales.csv:
     * 386 de 1000 filas omitidas (el estimado inicial de 142 solo contaba
     * tipo de transaccion y monto invalido; fecha ilegible explica la
     * diferencia). 500 deja margen sobre eso.
     */
    @Value("${app.batch.estados-cuenta.skip-limit:500}")
    private int skipLimit;

    @Bean
    public FlatFileItemReader<MovimientoRaw> movimientoItemReader() {
        return new FlatFileItemReaderBuilder<MovimientoRaw>()
                .name("movimientoItemReader")
                .resource(cuentasAnualesCsv)
                .linesToSkip(1) // encabezado: cuenta_id,fecha,transaccion,monto,descripcion
                .delimited()
                .names("cuentaId", "fecha", "transaccion", "monto", "descripcion")
                .targetType(MovimientoRaw.class)
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<MovimientoValidado> movimientoItemWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<MovimientoValidado>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO movimiento_cuenta_anual (cuenta_id, fecha, transaccion, monto, descripcion)
                        VALUES (:cuentaId, :fecha, :transaccion, :monto, :descripcion)
                        """)
                .beanMapped()
                .build();
    }

    @Bean
    public Step limpiarEstadosCuentaStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          JdbcTemplate jdbcTemplate) {
        return new StepBuilder("limpiarEstadosCuentaStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    jdbcTemplate.update("DELETE FROM movimiento_cuenta_anual");
                    jdbcTemplate.update("DELETE FROM estado_cuenta_anual");
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public LoggingSkipListener<MovimientoRaw, MovimientoValidado> movimientoSkipListener() {
        return new LoggingSkipListener<>("annualStatementJob");
    }

    @Bean
    public PerformanceStepListener movimientoPerformanceListener() {
        return new PerformanceStepListener();
    }

    @Bean
    public Step cargarMovimientosStep(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager,
                                       FlatFileItemReader<MovimientoRaw> movimientoItemReader,
                                       ItemProcessor<MovimientoRaw, MovimientoValidado> movimientoItemProcessor,
                                       JdbcBatchItemWriter<MovimientoValidado> movimientoItemWriter,
                                       LoggingSkipListener<MovimientoRaw, MovimientoValidado> movimientoSkipListener,
                                       PerformanceStepListener movimientoPerformanceListener,
                                       BackOffPolicy transientErrorBackOffPolicy) {
        return new StepBuilder("cargarMovimientosStep", jobRepository)
                // Semana 3: chunk configurable (app.batch.estados-cuenta.chunk-size),
                // SIN taskExecutor — ver el javadoc de la clase para el porque.
                .<MovimientoRaw, MovimientoValidado>chunk(chunkSize, transactionManager)
                .reader(movimientoItemReader)
                .processor(movimientoItemProcessor)
                .writer(movimientoItemWriter)
                .faultTolerant()
                // Politica de OMISION: movimientos con datos irrecuperables (fecha
                // ilegible, monto invalido, tipo desconocido) o filas mal formadas.
                // La descripcion vacia NO cae aqui: se corrige en el processor sin
                // descartar la fila.
                .skip(DatoInvalidoException.class)
                .skip(FlatFileParseException.class)
                // Sin noRollback, el rollbackClassifier por defecto revierte la
                // transaccion del chunk aunque la excepcion sea skippable, lo que
                // deshace tambien el INSERT que AnomaliaAuditor ya hizo en
                // anomalia_dato antes de que MovimientoItemProcessor lanzara
                // DatoInvalidoException. No aplica a FlatFileParseException: esa
                // ocurre en la lectura, antes de que exista algo que auditar.
                .noRollback(DatoInvalidoException.class)
                .skipLimit(skipLimit)
                .listener(movimientoSkipListener)
                // Politica de REINTENTO: errores transitorios de base de datos.
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                // Semana 3: backoff exponencial, ver javadoc de BatchRetryConfig.
                .backOffPolicy(transientErrorBackOffPolicy)
                // IMPORTANTE: ver el comentario equivalente en
                // TransaccionesDiariasJobConfig.cargarTransaccionesWorkerStep — el
                // listener de StepExecutionListener debe ir al final, despues de
                // .retry()/.retryLimit(), o el compilador pierde esos metodos.
                .listener(movimientoPerformanceListener)
                .build();
    }

    @Bean
    public Step generarEstadoCuentaAnualStep(JobRepository jobRepository,
                                              PlatformTransactionManager transactionManager,
                                              JdbcTemplate jdbcTemplate) {
        return new StepBuilder("generarEstadoCuentaAnualStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    jdbcTemplate.update("""
                            INSERT INTO estado_cuenta_anual
                                (cuenta_id, anio, total_depositos, total_retiros, total_compras,
                                 saldo_neto, cantidad_movimientos, fecha_generacion)
                            SELECT
                                cuenta_id,
                                CAST(EXTRACT(YEAR FROM fecha) AS INTEGER),
                                COALESCE(SUM(CASE WHEN transaccion = 'deposito' THEN monto ELSE 0 END), 0),
                                COALESCE(SUM(CASE WHEN transaccion = 'retiro' THEN monto ELSE 0 END), 0),
                                COALESCE(SUM(CASE WHEN transaccion = 'compra' THEN monto ELSE 0 END), 0),
                                COALESCE(SUM(monto), 0),
                                COUNT(*),
                                CURRENT_TIMESTAMP
                            FROM movimiento_cuenta_anual
                            GROUP BY cuenta_id, CAST(EXTRACT(YEAR FROM fecha) AS INTEGER)
                            """);
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Job annualStatementJob(JobRepository jobRepository,
                                   JobCompletionNotificationListener listener,
                                   Step limpiarEstadosCuentaStep,
                                   Step cargarMovimientosStep,
                                   Step generarEstadoCuentaAnualStep) {
        return new JobBuilder("annualStatementJob", jobRepository)
                .listener(listener)
                .start(limpiarEstadosCuentaStep)
                .next(cargarMovimientosStep)
                .next(generarEstadoCuentaAnualStep)
                .build();
    }
}
