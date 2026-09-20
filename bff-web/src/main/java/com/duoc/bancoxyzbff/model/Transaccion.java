package com.duoc.bancoxyzbff.model;

import java.time.LocalDate;

/**
 * Representa una transaccion diaria general del banco.
 * Viene del archivo "transacciones.csv" (id, fecha, monto, tipo).
 * A diferencia de los movimientos anuales, este archivo NO tiene
 * vinculo directo con cuenta_id, por lo que se expone como actividad
 * general del banco (por ejemplo, para un dashboard del BFF Web).
 */
public class Transaccion {

    private Long id;
    private LocalDate fecha;
    private Double monto;
    private String tipo;

    public Transaccion() {
    }

    public Transaccion(Long id, LocalDate fecha, Double monto, String tipo) {
        this.id = id;
        this.fecha = fecha;
        this.monto = monto;
        this.tipo = tipo;
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
