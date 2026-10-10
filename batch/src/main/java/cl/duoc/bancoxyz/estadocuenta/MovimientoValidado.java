package cl.duoc.bancoxyz.estadocuenta;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Movimiento anual de una cuenta, ya validado y con la descripcion
 * corregida cuando venia vacia, listo para persistirse en
 * {@code movimiento_cuenta_anual}.
 */
public class MovimientoValidado {

    private Long cuentaId;
    private LocalDate fecha;
    private String transaccion;
    private BigDecimal monto;
    private String descripcion;

    public MovimientoValidado(Long cuentaId, LocalDate fecha, String transaccion,
                               BigDecimal monto, String descripcion) {
        this.cuentaId = cuentaId;
        this.fecha = fecha;
        this.transaccion = transaccion;
        this.monto = monto;
        this.descripcion = descripcion;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getTransaccion() {
        return transaccion;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
