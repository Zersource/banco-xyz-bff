package cl.duoc.bancoxyz.intereses;

import cl.duoc.bancoxyz.common.AnomaliaAuditor;
import cl.duoc.bancoxyz.common.DatoInvalidoException;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Valida cada cuenta de {@code intereses.csv} y calcula el interes mensual
 * segun el tipo de producto.
 * <p>
 * Reglas de negocio (a partir de los problemas simulados en el dataset:
 * edades no validas, saldos vacios y registros duplicados):
 * <ol>
 *     <li>{@code cuenta_id} debe ser numerico.</li>
 *     <li>{@code edad} debe estar en un rango humano razonable (1-120).</li>
 *     <li>{@code saldo} no puede estar vacio ni ser negativo (saldo en
 *     cero SI es valido: representa una cuenta vigente sin fondos).</li>
 *     <li>{@code tipo} debe ser ahorro, prestamo o hipoteca; cada uno tiene
 *     una tasa de interes mensual distinta (regla de negocio configurable
 *     en {@link #TASAS_POR_TIPO}).</li>
 * </ol>
 * <b>Importante sobre duplicados:</b> al revisar el dataset completo se
 * confirmo que el duplicado real NO repite {@code cuenta_id}: son dos
 * filas con {@code cuenta_id} distinto pero el mismo
 * nombre+saldo+edad+tipo (ej. cuenta_id 101 y 106, ambas
 * "John Doe,5000,30,ahorro").
 * <p>
 * <b>Deteccion de duplicados (cambio en semana 2):</b> en semana 1 esto se
 * resolvia con dos {@code Set} en memoria (uno para {@code cuenta_id}, otro
 * para la clave de contenido), poblados en {@code ItemWriteListener.afterWrite()}
 * y dependientes de {@code chunk(1, ...)} para ser confiables. Con
 * {@code chunk(5, ...)} y 3 hilos en paralelo (semana 2) ese diseño deja de
 * ser correcto: ver el javadoc equivalente en {@code TransaccionItemProcessor}
 * para el razonamiento completo. Ahora la unicidad la garantiza la base de
 * datos: {@code cuenta_id} ya es {@code PRIMARY KEY} de
 * {@code cuenta_interes_mensual}, y se agrego un constraint {@code UNIQUE}
 * sobre (nombre, saldo_inicial, edad, tipo) para la clave de contenido. El
 * {@code INSERT} duplicado lanza {@code DuplicateKeyException}, configurada
 * como excepcion "skippable" en {@code InteresesMensualesJobConfig}.
 * <p>
 * <b>Limitacion conocida:</b> con datasets sinteticos grandes y un
 * catalogo pequeno de nombres/valores (como la semana 3 del dataset de
 * referencia), la clave de contenido puede producir coincidencias
 * accidentales entre clientes distintos. Para este ejercicio se prioriza
 * detectar el patron de duplicado documentado en el dataset; en un
 * sistema real se usaria un identificador unico de cliente (RUT, etc.)
 * en vez de nombre+saldo+edad+tipo.
 */
@Component
public class InteresItemProcessor implements ItemProcessor<CuentaInteresRaw, CuentaInteresProcesada> {

    private static final String JOB = "monthlyInterestJob";

    /** Tasas de interes mensual por tipo de producto (regla de negocio simulada). */
    private static final Map<String, BigDecimal> TASAS_POR_TIPO = Map.of(
            "ahorro", new BigDecimal("0.005"),   // 0.5% mensual
            "prestamo", new BigDecimal("0.012"), // 1.2% mensual
            "hipoteca", new BigDecimal("0.008")  // 0.8% mensual
    );

    private static final int EDAD_MINIMA = 1;
    private static final int EDAD_MAXIMA = 120;

    @Autowired
    private AnomaliaAuditor anomaliaAuditor;

    @Override
    public CuentaInteresProcesada process(CuentaInteresRaw item) {
        Long cuentaId = parsearCuentaId(item);

        Integer edad = parsearEdad(item.getEdad());
        if (edad == null || edad < EDAD_MINIMA || edad > EDAD_MAXIMA) {
            fallar("Edad fuera de rango valido (1-120) o no numerica", item);
        }

        BigDecimal saldo = parsearSaldo(item.getSaldo());
        if (saldo == null) {
            fallar("Saldo vacio o no numerico", item);
        }
        if (saldo.compareTo(BigDecimal.ZERO) < 0) {
            fallar("Saldo negativo, no es posible calcular interes", item);
        }

        String tipo = item.getTipo() == null ? "" : item.getTipo().trim().toLowerCase();
        BigDecimal tasa = TASAS_POR_TIPO.get(tipo);
        if (tasa == null) {
            fallar("Tipo de cuenta no reconocido (se esperaba ahorro/prestamo/hipoteca)", item);
        }

        String nombre = item.getNombre() == null || item.getNombre().isBlank()
                ? "Sin nombre registrado"
                : item.getNombre().trim();

        BigDecimal interesGenerado = saldo.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoFinal = saldo.add(interesGenerado).setScale(2, RoundingMode.HALF_UP);

        return new CuentaInteresProcesada(cuentaId, nombre, tipo, edad, saldo, tasa, interesGenerado, saldoFinal);
    }

    private Long parsearCuentaId(CuentaInteresRaw item) {
        try {
            return Long.parseLong(item.getCuentaId().trim());
        } catch (NumberFormatException | NullPointerException e) {
            fallar("cuenta_id no numerico o ausente", item);
            return null; // inalcanzable: fallar() siempre lanza excepcion
        }
    }

    private Integer parsearEdad(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parsearSaldo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void fallar(String detalle, CuentaInteresRaw item) {
        anomaliaAuditor.registrar(JOB, "interes", detalle, item.toString());
        throw new DatoInvalidoException(detalle);
    }
}
