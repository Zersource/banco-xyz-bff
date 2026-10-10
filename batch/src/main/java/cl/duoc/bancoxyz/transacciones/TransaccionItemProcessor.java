package cl.duoc.bancoxyz.transacciones;

import cl.duoc.bancoxyz.common.AnomaliaAuditor;
import cl.duoc.bancoxyz.common.DatoInvalidoException;
import cl.duoc.bancoxyz.common.FechaUtils;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Valida y normaliza cada fila de {@code transacciones.csv}.
 * <p>
 * Reglas de negocio aplicadas (detectadas a partir de los problemas
 * simulados en el dataset legacy: montos negativos o en cero, fechas en
 * multiples formatos y filas duplicadas):
 * <ol>
 *     <li>El {@code id} y el {@code monto} deben ser numericos.</li>
 *     <li>El {@code monto} debe ser estrictamente positivo.</li>
 *     <li>La {@code fecha} se interpreta de forma flexible (ver
 *     {@link FechaUtils}).</li>
 *     <li>El {@code tipo} debe ser "debito" o "credito".</li>
 * </ol>
 * <b>Sobre duplicados (cambio en semana 2):</b> en la semana 1 la deteccion
 * de filas duplicadas (misma fecha+monto+tipo) se hacia con un {@code Set}
 * en memoria, poblado en {@code ItemWriteListener.afterWrite()} para evitar
 * que el re-escaneo de {@code faultTolerant().skip()} los marcara como
 * duplicados de si mismos. Ese diseño dependia de {@code chunk(1, ...)}
 * para ser confiable (con chunk &gt; 1, todos los {@code process()} de un
 * chunk corren antes que el primer {@code write()}, asi que dos duplicados
 * reales en el mismo chunk pasaban ambos como validos).
 * <p>
 * En semana 2 el step pasa a {@code chunk(5, ...)} con 3 hilos en paralelo,
 * donde ademas el orden de llegada de {@code afterWrite()} entre chunks
 * concurrentes no es determinista. La deteccion de duplicados por estado en
 * memoria ya no es viable. Se reemplaza por un constraint {@code UNIQUE}
 * sobre (fecha, monto, tipo) en la tabla {@code transaccion_validada}: el
 * {@code INSERT} duplicado lanza {@code DuplicateKeyException}, configurada
 * como excepcion "skippable" en {@code TransaccionesDiariasJobConfig}. Esto
 * es correcto sea cual sea el chunk size o el numero de hilos, porque la
 * garantia de unicidad la da la base de datos y no el orden de ejecucion.
 * <p>
 * Los casos irrecuperables (formato invalido) se auditan en
 * {@code anomalia_dato} y luego se propagan como
 * {@link DatoInvalidoException}, que el step correspondiente tiene
 * configurado como excepcion "skippable" (mecanismo nativo de Spring
 * Batch), en vez de simplemente descartar la fila a mano. Los duplicados no
 * se auditan aca porque, con este diseño, el processor ya no sabe si un
 * item es duplicado — solo el intento de escritura lo revela.
 */
@Component
public class TransaccionItemProcessor implements ItemProcessor<TransaccionRaw, TransaccionValidada> {

    private static final String JOB = "dailyTransactionsJob";
    private static final Set<String> TIPOS_VALIDOS = Set.of("debito", "credito");

    @Autowired
    private AnomaliaAuditor anomaliaAuditor;

    @Override
    public TransaccionValidada process(TransaccionRaw item) {
        Long id = parsearId(item);

        LocalDate fecha = FechaUtils.parsearFechaFlexible(item.getFecha());
        if (fecha == null) {
            fallar("Formato de fecha invalido (se esperaba yyyy-MM-dd, yyyy/MM/dd, dd-MM-yyyy o dd/MM/yyyy)", item);
        }

        BigDecimal monto = parsearMonto(item.getMonto());
        if (monto == null) {
            fallar("Monto no numerico", item);
        }
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            fallar("Monto invalido: negativo o cero", item);
        }

        String tipo = item.getTipo() == null ? "" : item.getTipo().trim().toLowerCase();
        if (!TIPOS_VALIDOS.contains(tipo)) {
            fallar("Tipo de transaccion no reconocido (se esperaba debito/credito)", item);
        }

        return new TransaccionValidada(id, fecha, monto, tipo);
    }

    private Long parsearId(TransaccionRaw item) {
        try {
            return Long.parseLong(item.getId().trim());
        } catch (NumberFormatException | NullPointerException e) {
            fallar("Id no numerico o ausente", item);
            return null; // inalcanzable: fallar() siempre lanza excepcion
        }
    }

    private BigDecimal parsearMonto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Audita la anomalia y detiene el procesamiento de la fila lanzando
     * {@link DatoInvalidoException}, que el step tiene configurado como
     * excepcion "skippable".
     */
    private void fallar(String detalle, TransaccionRaw item) {
        anomaliaAuditor.registrar(JOB, "transaccion", detalle, item.toString());
        throw new DatoInvalidoException(detalle);
    }
}
