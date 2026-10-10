package com.duoc.bancoxyzbff.dto;

/**
 * Espejo del modelo Cuenta del microservicio cuentas, usado solo para
 * deserializar GET /cuentas/{id}. Cada microservicio es independiente, asi
 * que no comparte la clase original.
 */
public class CuentaDTO {

    private Long cuentaId;
    private Double saldo;
    private String tipo;

    public CuentaDTO() {
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
