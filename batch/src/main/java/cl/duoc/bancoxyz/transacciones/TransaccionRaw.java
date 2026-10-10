package cl.duoc.bancoxyz.transacciones;

/**
 * Representa una fila cruda de {@code transacciones.csv}, tal como viene del
 * sistema legacy, sin ninguna validacion aplicada todavia.
 * <p>
 * Todos los campos se leen como {@link String} a proposito: de esta forma
 * el {@code ItemReader} nunca falla por un valor mal formado (por ejemplo,
 * un monto vacio), y es el {@link TransaccionItemProcessor} quien decide,
 * de forma controlada, si el registro es valido, corregible o debe
 * descartarse.
 */
public class TransaccionRaw {

    private String id;
    private String fecha;
    private String monto;
    private String tipo;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getMonto() {
        return monto;
    }

    public void setMonto(String monto) {
        this.monto = monto;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    @Override
    public String toString() {
        return "TransaccionRaw{id=%s, fecha=%s, monto=%s, tipo=%s}".formatted(id, fecha, monto, tipo);
    }
}
