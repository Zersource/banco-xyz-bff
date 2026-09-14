package com.duoc.bancoxyzbff.model;

/**
 * Representa una cuenta/cliente del banco.
 * IMPORTANTE: estos datos vienen del archivo "intereses.csv" del dataset legacy,
 * pero pese al nombre del archivo, en realidad contiene el maestro de cuentas
 * (cuenta_id, nombre, saldo, edad, tipo). Se detecto esto en Exp1 y se mantiene
 * el mismo criterio aca: el modelo se llama segun lo que el dato realmente es,
 * no segun el nombre del archivo de origen.
 */
public class Cuenta {

    private Long cuentaId;
    private String nombre;
    private Double saldo;
    private Integer edad;
    private String tipo;

    public Cuenta() {
    }

    public Cuenta(Long cuentaId, String nombre, Double saldo, Integer edad, String tipo) {
        this.cuentaId = cuentaId;
        this.nombre = nombre;
        this.saldo = saldo;
        this.edad = edad;
        this.tipo = tipo;
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

    public Double getSaldo() {
        return saldo;
    }

    public void setSaldo(Double saldo) {
        this.saldo = saldo;
    }

    public Integer getEdad() {
        return edad;
    }

    public void setEdad(Integer edad) {
        this.edad = edad;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
