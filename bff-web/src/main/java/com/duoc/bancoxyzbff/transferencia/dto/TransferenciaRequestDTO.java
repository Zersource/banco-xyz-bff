package com.duoc.bancoxyzbff.transferencia.dto;

import java.math.BigDecimal;

/**
 * Solicitud de transferencia que llega al canal web y se reenvia a pagos
 * (las validaciones las hace pagos).
 */
public class TransferenciaRequestDTO {

    private Long cuentaOrigenId;
    private Long cuentaDestinoId;
    private BigDecimal monto;

    public Long getCuentaOrigenId() {
        return cuentaOrigenId;
    }

    public void setCuentaOrigenId(Long cuentaOrigenId) {
        this.cuentaOrigenId = cuentaOrigenId;
    }

    public Long getCuentaDestinoId() {
        return cuentaDestinoId;
    }

    public void setCuentaDestinoId(Long cuentaDestinoId) {
        this.cuentaDestinoId = cuentaDestinoId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }
}
