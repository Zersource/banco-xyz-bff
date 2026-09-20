package com.duoc.bffcajero.bff.cajero.dto;

public class RetiroResponseDTO {

    private Long cuentaId;
    private Double montoRetirado;
    private Double saldoResultante;

    public RetiroResponseDTO(Long cuentaId, Double montoRetirado, Double saldoResultante) {
        this.cuentaId = cuentaId;
        this.montoRetirado = montoRetirado;
        this.saldoResultante = saldoResultante;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public Double getMontoRetirado() {
        return montoRetirado;
    }

    public Double getSaldoResultante() {
        return saldoResultante;
    }
}
