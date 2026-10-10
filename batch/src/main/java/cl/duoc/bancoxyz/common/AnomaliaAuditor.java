package cl.duoc.bancoxyz.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Registra en la tabla {@code anomalia_dato} y en el log de la aplicacion
 * cada registro de origen que fue corregido, descartado o marcado como
 * sospechoso durante el procesamiento batch.
 * <p>
 * Este componente es el punto central de "manejo de errores y excepciones"
 * exigido por la actividad: en lugar de dejar que un registro con datos
 * incorrectos detenga el Job completo, se audita y se continua el proceso.
 * <p>
 * <b>Cambio en semana 2 — {@code REQUIRES_NEW}:</b> con {@code chunk(5)} y 3
 * hilos en paralelo, el ItemProcessor puede auditar un dato invalido
 * ({@code DatoInvalidoException}, marcada {@code noRollback} en el Step) en
 * el mismo chunk fisico donde otro item distinto falla al escribir por
 * duplicado ({@code DuplicateKeyException}, que SI hace rollback). Spring
 * Batch revierte la transaccion completa del chunk cuando cualquier
 * excepcion no marcada como {@code noRollback} participa en el fallo, sin
 * importar que otra excepcion si lo estuviera — lo que deshacia tambien el
 * INSERT de auditoria ya hecho. Se confirmo empiricamente corriendo el test
 * de integracion con chunk(5): se perdia una fila de {@code anomalia_dato}
 * que si se auditaba correctamente en chunk(1) (semana 1).
 * <p>
 * {@code Propagation.REQUIRES_NEW} hace que cada llamada a
 * {@link #registrar} se comprometa en su propia transaccion, independiente
 * de la transaccion del chunk que la invoca. Asi la auditoria sobrevive sin
 * importar si el resto del chunk termina en rollback, sin depender del
 * mecanismo de re-escaneo item-por-item de {@code faultTolerant()} (que
 * Spring Batch mismo advierte como no del todo confiable al combinarse con
 * un {@code taskExecutor} multihilo: ver el WARN
 * "Asynchronous TaskExecutor detected with ItemStream reader" en el log de
 * arranque de cada Step).
 */
@Component
public class AnomaliaAuditor {

    private static final Logger log = LoggerFactory.getLogger(AnomaliaAuditor.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Registra una anomalia de datos detectada durante el procesamiento.
     *
     * @param job       nombre del Job donde se detecto la anomalia
     * @param origen    nombre del step/etapa (ej: "transaccion", "interes")
     * @param detalle   descripcion legible de la anomalia
     * @param datoCrudo representacion textual del registro original
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String job, String origen, String detalle, String datoCrudo) {
        log.warn("[{}][{}] Anomalia detectada: {} | dato original: {}", job, origen, detalle, datoCrudo);
        jdbcTemplate.update(
                "INSERT INTO anomalia_dato (job_name, origen, detalle, dato_crudo, fecha_deteccion) " +
                        "VALUES (?, ?, ?, ?, ?)",
                job, origen, detalle, datoCrudo, Timestamp.valueOf(LocalDateTime.now())
        );
    }
}
