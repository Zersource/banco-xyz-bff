package cl.duoc.bancoxyz.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Utilidad para interpretar fechas provenientes del sistema legacy.
 * <p>
 * Al revisar el dataset completo de referencia (no solo la muestra de la
 * semana 1) se detectaron <b>cuatro</b> formatos de fecha mezclados en los
 * archivos legacy:
 * <ul>
 *     <li>{@code yyyy-MM-dd} (estandar)</li>
 *     <li>{@code yyyy/MM/dd}</li>
 *     <li>{@code dd-MM-yyyy}</li>
 *     <li>{@code dd/MM/yyyy}</li>
 * </ul>
 * <b>Supuesto de negocio:</b> para los formatos de dos digitos ambiguos
 * ({@code dd-MM-yyyy} / {@code dd/MM/yyyy}) se asume dia-mes-anio (formato
 * latinoamericano), no mes-dia-anio (formato estadounidense). Esta
 * ambiguedad es inherente a cualquier migracion real desde un sistema
 * legacy sin metadatos de formato, y es exactamente el tipo de decision
 * que una propuesta tecnica de migracion debe dejar explicita.
 * <p>
 * Fechas invalidas en cualquier formato (por ejemplo, mes 13) fallan la
 * validacion de todos los patrones y se reportan como anomalia.
 */
public final class FechaUtils {

    private static final List<DateTimeFormatter> FORMATOS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );

    private FechaUtils() {
    }

    /**
     * Intenta parsear una fecha probando los formatos soportados.
     *
     * @param valor texto crudo leido del CSV
     * @return la fecha parseada, o {@code null} si el valor es nulo/vacio o
     *         no coincide con ningun formato soportado.
     */
    public static LocalDate parsearFechaFlexible(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String texto = valor.trim();
        for (DateTimeFormatter formato : FORMATOS) {
            try {
                return LocalDate.parse(texto, formato);
            } catch (DateTimeParseException ignored) {
                // se intenta el siguiente formato soportado
            }
        }
        return null;
    }
}
