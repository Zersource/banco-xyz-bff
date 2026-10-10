package cl.duoc.bancoxyz.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;

/**
 * {@link SkipListener} generico, instanciado una vez por Job (ver cada
 * {@code *JobConfig}), que deja evidencia en el log de cada fila omitida
 * por el mecanismo de <b>skip</b> de Spring Batch.
 * <p>
 * El detalle completo de la anomalia ya fue registrado en la tabla
 * {@code anomalia_dato} por {@link AnomaliaAuditor} antes de lanzar la
 * {@link DatoInvalidoException}; este listener es la confirmacion, a nivel
 * de framework, de que el step efectivamente omitio el registro y siguio
 * procesando el resto del archivo en lugar de abortar el Job.
 *
 * @param <T> tipo de entrada (item "crudo")
 * @param <S> tipo de salida (item procesado)
 */
public class LoggingSkipListener<T, S> implements SkipListener<T, S> {

    private static final Logger log = LoggerFactory.getLogger(LoggingSkipListener.class);

    private final String nombreJob;

    public LoggingSkipListener(String nombreJob) {
        this.nombreJob = nombreJob;
    }

    @Override
    public void onSkipInProcess(T item, Throwable t) {
        log.warn("[{}] SKIP en processor -> {} | item: {}", nombreJob, t.getMessage(), item);
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("[{}] SKIP en lectura (linea con formato incorrecto) -> {}", nombreJob, t.getMessage());
    }

    @Override
    public void onSkipInWrite(S item, Throwable t) {
        log.warn("[{}] SKIP en escritura -> {} | item: {}", nombreJob, t.getMessage(), item);
    }
}
