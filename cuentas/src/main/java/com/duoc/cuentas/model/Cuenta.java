package com.duoc.cuentas.model;

/**
 * Representa el saldo de una cuenta del banco. Los datos vienen del archivo
 * "intereses.csv" del dataset legacy que, pese a su nombre, contiene el
 * maestro de cuentas (cuenta_id, nombre, saldo, edad, tipo). En EFT el
 * maestro se reparte: esta clase se queda con lo que es de la cuenta
 * (cuenta_id, saldo, tipo) y el nombre y la edad pasan al servicio clientes.
 */
public class Cuenta {

    private Long cuentaId;
    private Double saldo;
    private String tipo;

    public Cuenta() {
    }

    public Cuenta(Long cuentaId, Double saldo, String tipo) {
        this.cuentaId = cuentaId;
        this.saldo = saldo;
        this.tipo = tipo;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public Double getSaldo() {
        return saldo;
    }

    public void setSaldo(Double saldo) {
        this.saldo = saldo;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
