package com.duoc.clientes.model;

/**
 * Titular de una cuenta. Sale de las columnas cuenta_id, nombre y edad de
 * "intereses.csv" (el saldo y el tipo son del servicio cuentas).
 */
public class Cliente {

    private Long cuentaId;
    private String nombre;
    private Integer edad;

    public Cliente() {
    }

    public Cliente(Long cuentaId, String nombre, Integer edad) {
        this.cuentaId = cuentaId;
        this.nombre = nombre;
        this.edad = edad;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Integer getEdad() {
        return edad;
    }

    public void setEdad(Integer edad) {
        this.edad = edad;
    }
}
