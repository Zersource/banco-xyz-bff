package cl.duoc.bancoxyz.intereses;

import cl.duoc.bancoxyz.common.DatoInvalidoException;
import cl.duoc.bancoxyz.common.LoggingSkipListener;
import cl.duoc.bancoxyz.common.PerformanceStepListener;
import cl.duoc.bancoxyz.config.JobCompletionNotificationListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Job "Calculo de Intereses Mensuales".
 * <p>
 * Migra el proceso legacy que aplicaba intereses sobre cuentas de ahorro,
 * prestamos e hipotecas, actualizando el saldo final en base de datos.
 * Consta de dos steps: limpieza (idempotencia) y carga+calculo.
 * <p>
 * <b>Semana 3 — de multi-hilo a secuencial (hallazgo empirico):</b> en
 * semana 2 este Step corria con {@code chunk(5)} + 3 hilos + fault-tolerant
 * (retry/skip), y funcionaba bien con el CSV de 8 filas (2 chunks). Al
 * correrlo contra el dataset de semana 3 (1000 filas, ~200 chunks bajo
 * concurrencia real), quedo colgado indefinidamente: el hilo principal
 * bloqueado en {@code ResultHolderResultQueue.take()} esperando un
 * resultado que nunca llega, mientras los 3 hilos del pool quedan inactivos.
 * <p>
 * La causa no es un bug de nuestro codigo sino una combinacion que Spring
 * Batch no garantiza segura: un Step multi-hilo (no particionado) con
 * {@code .faultTolerant()} activo. El manejo interno de reintentos dentro
 * de un chunk no esta pensado para que varios hilos lo toquen a la vez, y
 * bajo suficiente volumen esa condicion de carrera se manifiesta. Es el
 * mismo principio que ya motivo migrar {@code TransaccionesDiariasJobConfig}
 * a particiones (ahi cada particion tiene su propio Step, sin ese riesgo).
 * <p>
 * Con solo 1000 filas, mantener este Step secuencial (sin
 * {@code taskExecutor}) es una perdida de rendimiento aceptable frente al
 * riesgo real de que el Job se cuelgue en produccion. Las politicas de
 * skip/retry/backoff se mantienen exactamente iguales.
 */
@Configuration
public class InteresesMensualesJobConfig {

    @Value("${app.csv.intereses:classpath:data/semana_1/intereses.csv}")
    private Resource interesesCsv;

    /**
     * Semana 3: default subido de 5 a 20 tras el benchmark de escalado (ver
     * PROPUESTA_TECNICA.md) — capta casi toda la ganancia medida (1028 ms
     * en chunk=5 vs 800 ms en chunk=20); subir a 50 solo baja a 699 ms, ya
     * no se justifica el chunk mas grande por esa diferencia.
     */
    @Value("${app.batch.intereses.chunk-size:20}")
    private int chunkSize;

    /**
     * Semana 3: mismo motivo que en TransaccionesDiariasJobConfig. Medido
     * sobre data_referencia/semana_3/intereses.csv corriendo el Job real:
     * 950 de 1000 filas omitidas (601 por regla de negocio invalida + 349
     * por el constraint UNIQUE nombre/saldo/edad/tipo — el catalogo
     * sintetico de semana_3 solo tiene 8 nombres distintos, asi que la
     * gran mayoria de las filas que sí pasan las reglas de negocio terminan
     * siendo duplicados). 1000 (el maximo posible dado el tamaño del CSV)
     * evita tener que recalcular este numero si el dataset cambia de
     * proporcion en el futuro.
     */
    @Value("${app.batch.intereses.skip-limit:1000}")
    private int skipLimit;

    @Bean
    public FlatFileItemReader<CuentaInteresRaw> interesItemReader() {
        return new FlatFileItemReaderBuilder<CuentaInteresRaw>()
                .name("interesItemReader")
                .resource(interesesCsv)
                .linesToSkip(1) // encabezado: cuenta_id,nombre,saldo,edad,tipo
                .delimited()
                .names("cuentaId", "nombre", "saldo", "edad", "tipo")
                .targetType(CuentaInteresRaw.class)
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<CuentaInteresProcesada> interesItemWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<CuentaInteresProcesada>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO cuenta_interes_mensual
                            (cuenta_id, nombre, tipo, edad, saldo_inicial, tasa_interes,
                             interes_generado, saldo_final, fecha_proceso)
                        VALUES (:cuentaId, :nombre, :tipo, :edad, :saldoInicial, :tasaInteres,
                                :interesGenerado, :saldoFinal, :fechaProceso)
                        """)
                .beanMapped()
                .build();
    }

    @Bean
    public Step limpiarInteresesStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      JdbcTemplate jdbcTemplate) {
        return new StepBuilder("limpiarInteresesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    jdbcTemplate.update("DELETE FROM cuenta_interes_mensual");
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public LoggingSkipListener<CuentaInteresRaw, CuentaInteresProcesada> interesSkipListener() {
        return new LoggingSkipListener<>("monthlyInterestJob");
    }

    @Bean
    public PerformanceStepListener interesPerformanceListener() {
        return new PerformanceStepListener();
    }

    @Bean
    public Step calcularInteresesStep(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager,
                                       FlatFileItemReader<CuentaInteresRaw> interesItemReader,
                                       InteresItemProcessor interesItemProcessor,
                                       JdbcBatchItemWriter<CuentaInteresProcesada> interesItemWriter,
                                       LoggingSkipListener<CuentaInteresRaw, CuentaInteresProcesada> interesSkipListener,
                                       PerformanceStepListener interesPerformanceListener,
                                       BackOffPolicy transientErrorBackOffPolicy) {
        return new StepBuilder("calcularInteresesStep", jobRepository)
                // Semana 3: chunk configurable (app.batch.intereses.chunk-size), SIN
                // taskExecutor — ver el javadoc de la clase para el porque.
                .<CuentaInteresRaw, CuentaInteresProcesada>chunk(chunkSize, transactionManager)
                .reader(interesItemReader)
                .processor(interesItemProcessor)
                .writer(interesItemWriter)
                .faultTolerant()
                // Politica de OMISION: cuentas con datos irrecuperables (edad fuera de
                // rango, saldo invalido, tipo desconocido) o filas mal formadas en el
                // CSV, mas duplicados detectados por los constraints al escribir.
                .skip(DatoInvalidoException.class)
                .skip(FlatFileParseException.class)
                .skip(DuplicateKeyException.class)
                // Sin noRollback, el rollbackClassifier por defecto revierte la
                // transaccion del chunk aunque la excepcion sea skippable, lo que
                // deshace tambien el INSERT que AnomaliaAuditor ya hizo en
                // anomalia_dato antes de que InteresItemProcessor lanzara
                // DatoInvalidoException. No aplica a FlatFileParseException ni a
                // DuplicateKeyException por el mismo motivo que en
                // TransaccionesDiariasJobConfig.
                .noRollback(DatoInvalidoException.class)
                .skipLimit(skipLimit)
                .listener(interesSkipListener)
                // Politica de REINTENTO: errores transitorios de base de datos.
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                // Semana 3: backoff exponencial, ver javadoc de BatchRetryConfig.
                .backOffPolicy(transientErrorBackOffPolicy)
                // IMPORTANTE: ver el comentario equivalente en
                // TransaccionesDiariasJobConfig.cargarTransaccionesWorkerStep — el
                // listener de StepExecutionListener debe ir al final, despues de
                // .retry()/.retryLimit(), o el compilador pierde esos metodos.
                .listener(interesPerformanceListener)
                .build();
    }

    @Bean
    public Job monthlyInterestJob(JobRepository jobRepository,
                                   JobCompletionNotificationListener listener,
                                   Step limpiarInteresesStep,
                                   Step calcularInteresesStep) {
        return new JobBuilder("monthlyInterestJob", jobRepository)
                .listener(listener)
                .start(limpiarInteresesStep)
                .next(calcularInteresesStep)
                .build();
    }
}

