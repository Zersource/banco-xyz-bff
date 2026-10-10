package cl.duoc.bancoxyz.transacciones;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Transaccion diaria ya validada y normalizada, lista para persistirse.
 */
public class TransaccionValidada {

    private Long id;
    private LocalDate fecha;
    private BigDecimal monto;
    private String tipo;

    public TransaccionValidada(Long id, LocalDate fecha, BigDecimal monto, String tipo) {
        this.id = id;
        this.fecha = fecha;
        this.monto = monto;
        this.tipo = tipo;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public String getTipo() {
        return tipo;
    }
}
