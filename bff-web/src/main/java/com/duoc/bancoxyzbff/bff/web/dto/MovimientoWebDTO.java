package com.duoc.bancoxyzbff.bff.web.dto;

import com.duoc.bancoxyzbff.model.MovimientoAnual;

import java.time.LocalDate;

/**
 * DTO de movimiento para el canal Web: incluye todos los campos,
 * ya que la interfaz web puede mostrar el detalle completo.
 */
public class MovimientoWebDTO {

    private LocalDate fecha;
    private String transaccion;
    private Double monto;
    private String descripcion;

    public MovimientoWebDTO(MovimientoAnual movimiento) {
        this.fecha = movimiento.getFecha();
        this.transaccion = movimiento.getTransaccion();
        this.monto = movimiento.getMonto();
        this.descripcion = movimiento.getDescripcion();
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getTransaccion() {
        return transaccion;
    }

    public Double getMonto() {
        return monto;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
