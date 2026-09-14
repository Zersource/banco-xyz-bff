package com.duoc.bancoxyzbff.bff.cajero.dto;

/**
 * DTO minimo de consulta de saldo para el canal Cajero: solo cuentaId
 * y saldo. Sin nombre, edad ni historial, por tratarse de un canal
 * publico y sensible (pantalla de cajero automatico).
 */
public class SaldoDTO {

    private Long cuentaId;
    private Double saldo;

    public SaldoDTO(Long cuentaId, Double saldo) {
        this.cuentaId = cuentaId;
        this.saldo = saldo;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public Double getSaldo() {
        return saldo;
    }
}
