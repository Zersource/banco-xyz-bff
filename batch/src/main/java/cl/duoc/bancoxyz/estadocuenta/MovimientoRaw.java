package cl.duoc.bancoxyz.estadocuenta;

/**
 * Fila cruda de {@code cuentas_anuales.csv}, con el historial de
 * movimientos anuales de cada cuenta.
 */
public class MovimientoRaw {

    private String cuentaId;
    private String fecha;
    private String transaccion;
    private String monto;
    private String descripcion;

    public String getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(String cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getTransaccion() {
        return transaccion;
    }

    public void setTransaccion(String transaccion) {
        this.transaccion = transaccion;
    }

    public String getMonto() {
        return monto;
    }

    public void setMonto(String monto) {
        this.monto = monto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    @Override
    public String toString() {
        return "MovimientoRaw{cuentaId=%s, fecha=%s, transaccion=%s, monto=%s, descripcion=%s}"
                .formatted(cuentaId, fecha, transaccion, monto, descripcion);
    }
}
