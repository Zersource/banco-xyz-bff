package com.duoc.bancoxyzbff.transferencia.dto;

/**
 * Respuesta de pagos (espejo de su DTO). El estado viaja como texto
 * (PENDIENTE, COMPLETADA, FALLIDA o REVERTIDA).
 */
public class TransferenciaResponseDTO {

    private String transaccionId;
    private String estado;
    private String mensaje;

    public TransferenciaResponseDTO() {
    }

    public String getTransaccionId() {
        return transaccionId;
    }

    public void setTransaccionId(String transaccionId) {
        this.transaccionId = transaccionId;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
