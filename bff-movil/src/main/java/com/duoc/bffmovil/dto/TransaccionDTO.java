package com.duoc.bffmovil.dto;

import java.time.LocalDate;

/**
 * Espejo del modelo Transaccion de bff-web, usado para deserializar
 * GET /interno/transacciones/ultimas.
 */
public class TransaccionDTO {

    private Long id;
    private LocalDate fecha;
    private Double monto;
    private String tipo;

    public TransaccionDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public Double getMonto() {
        return monto;
    }

    public void setMonto(Double monto) {
        this.monto = monto;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
