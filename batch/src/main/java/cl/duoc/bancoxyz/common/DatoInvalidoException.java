package cl.duoc.bancoxyz.common;

/**
 * Excepcion de negocio para datos irrecuperables (fecha ilegible, tipo
 * desconocido, duplicado, etc.).
 * <p>
 * A diferencia de una simple correccion (por ejemplo, rellenar una
 * descripcion vacia), estos casos no pueden "arreglarse" dentro del
 * {@code ItemProcessor}: la fila debe omitirse. Se lanza esta excepcion en
 * lugar de simplemente retornar {@code null} para que sea el mecanismo de
 * <b>skip</b> de Spring Batch (step {@code faultTolerant().skip(...)}) el
 * que gestione la omision, tal como lo describe la guia de la actividad y
 * el propio dataset de origen ("gestionados mediante politicas de
 * reintento y omision en Spring Batch").
 */
public class DatoInvalidoException extends RuntimeException {

    public DatoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
