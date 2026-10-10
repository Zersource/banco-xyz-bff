package com.duoc.clientes.saga.evento;

import java.math.BigDecimal;

/**
 * Evento unico que viaja por todos los topics de la saga (value del mensaje
 * Kafka; la key es el transaccionId). Se reutiliza la misma clase para no
 * explotar en un DTO por cada paso; lo que cambia es el campo tipoEvento y
 * el topic de destino. Cada servicio tiene su propia copia de esta clase y
 * se entienden por el JSON.
 */
public class EventoTransferencia {

    private Long transaccionId;
    private Long cuentaOrigenId;
    private Long cuentaDestinoId;
    private BigDecimal monto;
    private TipoEventoTransferencia tipoEvento;
    private String motivo;

    public EventoTransferencia() {
    }

    public EventoTransferencia(Long transaccionId, Long cuentaOrigenId, Long cuentaDestinoId,
                                BigDecimal monto, TipoEventoTransferencia tipoEvento, String motivo) {
        this.transaccionId = transaccionId;
        this.cuentaOrigenId = cuentaOrigenId;
        this.cuentaDestinoId = cuentaDestinoId;
        this.monto = monto;
        this.tipoEvento = tipoEvento;
        this.motivo = motivo;
    }

    public Long getTransaccionId() {
        return transaccionId;
    }

    public void setTransaccionId(Long transaccionId) {
        this.transaccionId = transaccionId;
    }

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

    public TipoEventoTransferencia getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(TipoEventoTransferencia tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    @Override
    public String toString() {
        return "EventoTransferencia{" +
                "transaccionId=" + transaccionId +
                ", cuentaOrigenId=" + cuentaOrigenId +
                ", cuentaDestinoId=" + cuentaDestinoId +
                ", monto=" + monto +
                ", tipoEvento=" + tipoEvento +
                ", motivo='" + motivo + '\'' +
                '}';
    }
}
