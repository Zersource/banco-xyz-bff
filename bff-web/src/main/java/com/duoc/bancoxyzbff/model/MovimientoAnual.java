package com.duoc.bancoxyzbff.model;

import java.time.LocalDate;

/**
 * Representa un movimiento dentro del historial de una cuenta.
 * Estos datos vienen del archivo "cuentas_anuales.csv" del dataset legacy,
 * que en realidad contiene el historial de movimientos por cuenta
 * (cuenta_id, fecha, transaccion, monto, descripcion).
 */
public class MovimientoAnual {

    private Long cuentaId;
    private LocalDate fecha;
    private String transaccion;
    private Double monto;
    private String descripcion;

    public MovimientoAnual() {
    }

    public MovimientoAnual(Long cuentaId, LocalDate fecha, String transaccion, Double monto, String descripcion) {
        this.cuentaId = cuentaId;
        this.fecha = fecha;
        this.transaccion = transaccion;
        this.monto = monto;
        this.descripcion = descripcion;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getTransaccion() {
        return transaccion;
    }

    public void setTransaccion(String transaccion) {
        this.transaccion = transaccion;
    }

    public Double getMonto() {
        return monto;
    }

    public void setMonto(Double monto) {
        this.monto = monto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
