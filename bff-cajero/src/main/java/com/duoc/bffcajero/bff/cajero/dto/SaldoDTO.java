package com.duoc.bffcajero.bff.cajero.dto;

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
