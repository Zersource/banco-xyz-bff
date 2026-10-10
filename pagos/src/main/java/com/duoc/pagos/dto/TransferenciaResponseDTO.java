package com.duoc.pagos.dto;

import com.duoc.pagos.model.EstadoTransaccion;

/**
 * Respuesta inmediata del POST /transferencias. Como el flujo es
 * asincrono a partir del debito, el endpoint responde de inmediato con
 * estado PENDIENTE y el id de la transaccion; el estado final se
 * consulta con GET /transferencias/{id}.
 */
public class TransferenciaResponseDTO {

    private Long transaccionId;
    private EstadoTransaccion estado;
    private String mensaje;

    public TransferenciaResponseDTO() {
    }

    public TransferenciaResponseDTO(Long transaccionId, EstadoTransaccion estado, String mensaje) {
        this.transaccionId = transaccionId;
        this.estado = estado;
        this.mensaje = mensaje;
    }

    public Long getTransaccionId() {
        return transaccionId;
    }

    public void setTransaccionId(Long transaccionId) {
        this.transaccionId = transaccionId;
    }

    public EstadoTransaccion getEstado() {
        return estado;
    }

    public void setEstado(EstadoTransaccion estado) {
        this.estado = estado;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
