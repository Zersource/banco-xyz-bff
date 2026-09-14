package com.duoc.bancoxyzbff.bff.cajero.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DTO de entrada para solicitar un retiro desde el cajero.
 */
public class RetiroRequestDTO {

    @NotNull(message = "El monto a retirar es obligatorio")
    @Positive(message = "El monto a retirar debe ser mayor a cero")
    private Double monto;

    public Double getMonto() {
        return monto;
    }

    public void setMonto(Double monto) {
        this.monto = monto;
    }
}
