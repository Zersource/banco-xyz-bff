package cl.duoc.bancoxyz.estadocuenta;

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
 * Valida y normaliza cada movimiento de {@code cuentas_anuales.csv}.
 * <p>
 * Se distingue entre errores que obligan a descartar la fila (fecha
 * ilegible, monto no numerico/negativo/cero, tipo de transaccion
 * desconocido) y errores que se pueden <b>corregir</b> sin perder el
 * movimiento (descripcion vacia se reemplaza por un texto por defecto).
 * <p>
 * <b>Sobre montos negativos:</b> el README del dataset de origen
 * (KariVillagran/bank_legacy_data) documenta explicitamente "montos
 * negativos" como uno de los problemas simulados EN ESTE archivo. Se
 * evaluo la alternativa de aceptar montos negativos para retiro/compra
 * (semanticamente razonable: un retiro reduce el saldo), pero al revisar
 * el dataset completo (no solo la muestra de la semana 1) no existe un
 * patron de signo consistente por tipo de transaccion — hay depositos
 * negativos y retiros/compras positivos mezclados sin regla clara — lo
 * que confirma que el signo negativo es ruido inyectado y no una
 * convencion de negocio real. Por eso se exige monto positivo para los
 * tres tipos de movimiento, igual que en los otros dos procesos.
 */
@Component
public class MovimientoItemProcessor implements ItemProcessor<MovimientoRaw, MovimientoValidado> {

    private static final String JOB = "annualStatementJob";
    private static final Set<String> TIPOS_VALIDOS = Set.of("deposito", "retiro", "compra");
    private static final String DESCRIPCION_POR_DEFECTO = "Sin descripcion registrada";

    @Autowired
    private AnomaliaAuditor anomaliaAuditor;

    @Override
    public MovimientoValidado process(MovimientoRaw item) {
        Long cuentaId = parsearCuentaId(item);

        LocalDate fecha = FechaUtils.parsearFechaFlexible(item.getFecha());
        if (fecha == null) {
            fallar("Formato de fecha invalido (se esperaba yyyy-MM-dd, yyyy/MM/dd, dd-MM-yyyy o dd/MM/yyyy)", item);
        }

        String transaccion = item.getTransaccion() == null ? "" : item.getTransaccion().trim().toLowerCase();
        if (!TIPOS_VALIDOS.contains(transaccion)) {
            fallar("Tipo de transaccion no reconocido (se esperaba deposito/retiro/compra)", item);
        }

        BigDecimal monto = parsearMonto(item.getMonto());
        if (monto == null) {
            fallar("Monto no numerico", item);
        }
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            fallar("Monto invalido: negativo o cero", item);
        }

        String descripcion = item.getDescripcion();
        if (descripcion == null || descripcion.isBlank()) {
            anomaliaAuditor.registrar(JOB, "movimiento",
                    "Descripcion faltante, se reemplaza por valor por defecto (correccion, no se descarta la fila)",
                    item.toString());
            descripcion = DESCRIPCION_POR_DEFECTO;
        } else {
            descripcion = descripcion.trim();
        }

        return new MovimientoValidado(cuentaId, fecha, transaccion, monto, descripcion);
    }

    private Long parsearCuentaId(MovimientoRaw item) {
        try {
            return Long.parseLong(item.getCuentaId().trim());
        } catch (NumberFormatException | NullPointerException e) {
            fallar("cuenta_id no numerico o ausente", item);
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

    private void fallar(String detalle, MovimientoRaw item) {
        anomaliaAuditor.registrar(JOB, "movimiento", detalle, item.toString());
        throw new DatoInvalidoException(detalle);
    }
}
