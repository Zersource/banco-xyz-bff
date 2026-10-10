package cl.duoc.bancoxyz.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Listener comun a los tres Jobs. Al finalizar cada ejecucion, deja en el
 * log (y por lo tanto en la evidencia de ejecucion exigida por la
 * actividad) un resumen legible: estado final, duracion y cantidad de
 * anomalias detectadas durante esa corrida.
 */
@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobCompletionNotificationListener.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("==> Iniciando Job [{}] con parametros {}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            Integer anomalias = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM anomalia_dato WHERE job_name = ?", Integer.class, jobName);
            log.info("==> Job [{}] finalizado con EXITO. Anomalias detectadas y auditadas: {}", jobName, anomalias);
        } else {
            log.error("==> Job [{}] finalizado con ESTADO {}. Revisar el stacktrace anterior.",
                    jobName, jobExecution.getStatus());
            jobExecution.getAllFailureExceptions().forEach(ex -> log.error("Causa: ", ex));
        }
    }
}
