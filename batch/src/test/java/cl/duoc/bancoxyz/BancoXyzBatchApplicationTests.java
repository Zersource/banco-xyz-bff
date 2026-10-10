package cl.duoc.bancoxyz;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integracion de extremo a extremo: ejecutan cada Job contra los
 * CSV de ejemplo (semana 1) sobre una base H2 en memoria y verifican que:
 * <ul>
 *     <li>el Job termina en estado COMPLETED,</li>
 *     <li>los registros validos quedan persistidos,</li>
 *     <li>las filas con datos incorrectos del dataset legacy se auditan en
 *     {@code anomalia_dato} en lugar de romper el Job.</li>
 * </ul>
 */
@SpringBootTest
@SpringBatchTest
@ActiveProfiles("dev")
class BancoXyzBatchApplicationTests {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("dailyTransactionsJob")
    private Job dailyTransactionsJob;

    @Autowired
    @Qualifier("monthlyInterestJob")
    private Job monthlyInterestJob;

    @Autowired
    @Qualifier("annualStatementJob")
    private Job annualStatementJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        assertThat(dailyTransactionsJob).isNotNull();
        assertThat(monthlyInterestJob).isNotNull();
        assertThat(annualStatementJob).isNotNull();
    }

    @Test
    void dailyTransactionsJob_procesaCsvYAuditaAnomalias() throws Exception {
        JobExecution execution = jobLauncher.run(dailyTransactionsJob, parametrosUnicos());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Integer validas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaccion_validada", Integer.class);
        Integer anomalias = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM anomalia_dato WHERE job_name = 'dailyTransactionsJob'", Integer.class);

        // transacciones.csv (semana 1) trae 10 filas: id=3 (monto negativo) e id=4
        // (monto cero) son datos invalidos, auditados en anomalia_dato. id=8 es
        // duplicado de id=6 (misma fecha+monto+tipo): en semana 2 la deduplicacion
        // se movio al constraint UNIQUE de la tabla (ver TransaccionItemProcessor),
        // asi que se detecta y omite igual, pero ya no genera fila en
        // anomalia_dato (el processor ya no la ve como invalida, solo el INSERT
        // fallido en el writer la revela). 10 - 3 = 7 filas validas, 2 anomalias
        // auditadas.
        assertThat(validas).isEqualTo(7);
        assertThat(anomalias).isEqualTo(2);
    }

    @Test
    void monthlyInterestJob_calculaInteresesYAuditaAnomalias() throws Exception {
        JobExecution execution = jobLauncher.run(monthlyInterestJob, parametrosUnicos());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Integer procesadas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cuenta_interes_mensual", Integer.class);
        Integer anomalias = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM anomalia_dato WHERE job_name = 'monthlyInterestJob'", Integer.class);

        // intereses.csv (semana 1) trae 8 filas: cuenta_id=106 es duplicado de
        // cuenta_id=101 (mismo nombre+saldo+edad+tipo). En semana 2 esa
        // deduplicacion se detecta por el constraint UNIQUE al escribir (ver
        // InteresItemProcessor), no por el processor, asi que ya no genera fila
        // en anomalia_dato: se omite igual, pero sin auditoria. 8 - 1 = 7
        // validas, 0 anomalias auditadas.
        assertThat(procesadas).isEqualTo(7);
        assertThat(anomalias).isEqualTo(0);
    }

    @Test
    void annualStatementJob_generaEstadoDeCuentaAgregado() throws Exception {
        JobExecution execution = jobLauncher.run(annualStatementJob, parametrosUnicos());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Integer movimientos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_cuenta_anual", Integer.class);
        Integer estados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM estado_cuenta_anual", Integer.class);
        Integer anomalias = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM anomalia_dato WHERE job_name = 'annualStatementJob'", Integer.class);

        // cuentas_anuales.csv (semana 1) trae 9 filas: 3 con monto <= 0 (dos retiros
        // negativos legibles como error de signo y un deposito en cero) se omiten.
        // 9 - 3 = 6 movimientos validos, repartidos en 6 cuentas distintas (2024).
        assertThat(movimientos).isEqualTo(6);
        assertThat(estados).isEqualTo(6);
        assertThat(anomalias).isEqualTo(3);
    }

    private org.springframework.batch.core.JobParameters parametrosUnicos() {
        return new JobParametersBuilder()
                .addLong("timestamp", System.nanoTime())
                .toJobParameters();
    }
}
