package cl.duoc.bancoxyz.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * {@link StepExecutionListener} generico, instanciado una vez por Step de
 * carga (ver cada {@code *JobConfig}), que deja en el log metricas de
 * rendimiento de esa ejecucion: duracion real, cantidad de items leidos,
 * escritos y omitidos, cantidad de commits y throughput (items/segundo).
 * <p>
 * Requisito de la semana 2: "Implementa tecnicas de logs para evaluar el
 * rendimiento... ajustar configuraciones y asegurar la estabilidad del
 * entorno batch mediante monitoreo continuo". Con chunk(5) y 3 hilos en
 * paralelo, este log permite comparar el throughput real contra la
 * configuracion (chunk size, throttleLimit) y ajustarla si hiciera falta.
 * <p>
 * No mantiene estado propio entre invocaciones (no hay campos mutables):
 * toda la informacion sale de {@link StepExecution}, que Spring Batch ya
 * trackea de forma segura bajo concurrencia. Por eso una misma instancia
 * puede reutilizarse sin los problemas de estado en memoria que se
 * resolvieron en {@code TransaccionItemProcessor} / {@code InteresItemProcessor}.
 */
public class PerformanceStepListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(PerformanceStepListener.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("--> Step [{}] iniciado a las {}", stepExecution.getStepName(), LocalDateTime.now());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long ms = calcularDuracionMs(stepExecution);
        long leidos = stepExecution.getReadCount();
        double throughput = leidos * 1000.0 / ms;

        log.info("--> Step [{}] finalizado en {} ms | leidos={} escritos={} " +
                        "omitidos(lectura/proceso/escritura)={}/{}/{} commits={} throughput={} items/s",
                stepExecution.getStepName(),
                ms,
                leidos,
                stepExecution.getWriteCount(),
                stepExecution.getReadSkipCount(),
                stepExecution.getProcessSkipCount(),
                stepExecution.getWriteSkipCount(),
                stepExecution.getCommitCount(),
                String.format("%.2f", throughput));

        return stepExecution.getExitStatus();
    }

    private static long calcularDuracionMs(StepExecution stepExecution) {
        if (stepExecution.getStartTime() == null || stepExecution.getEndTime() == null) {
            return 0L;
        }
        // Minimo 1 ms para no dividir por cero calculando throughput en
        // steps extremadamente rapidos (datasets de prueba muy chicos).
        return Math.max(1L, Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime()).toMillis());
    }
}
