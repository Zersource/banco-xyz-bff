package com.duoc.bancoxyzbff.transferencia.listener;

import com.duoc.bancoxyzbff.transferencia.config.JmsConfig;
import com.duoc.bancoxyzbff.transferencia.evento.EventoTransferencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor final de la saga: escucha los tres eventos terminales
 * (completada, fallida, revertida) para dejar auditoria/notificacion.
 * El estado de la Transaccion ya lo dejo escrito el listener que detecto
 * el resultado (Debito/Credito/Compensacion); este listener representa
 * el "otro servicio que reacciona al mismo evento" que menciona la guia
 * (ej. notificaciones al cliente), sin tocar la base de datos.
 *
 * TODO: si se quiere notificacion real (correo/push), conectar aca con
 * el servicio correspondiente; por ahora deja evidencia en el log, que
 * es lo que se captura como "Prueba" en el informe.
 */
@Component
public class EstadoTransaccionListener {

    private static final Logger log = LoggerFactory.getLogger(EstadoTransaccionListener.class);

    @JmsListener(destination = JmsConfig.COLA_TRANSFERENCIA_COMPLETADA, concurrency = "2-3")
    public void manejarCompletada(EventoTransferencia evento) {
        log.info("[NOTIFICACION] Transferencia {} completada: {} -> {} por {}",
                evento.getTransaccionId(), evento.getCuentaOrigenId(),
                evento.getCuentaDestinoId(), evento.getMonto());
    }

    @JmsListener(destination = JmsConfig.COLA_TRANSFERENCIA_FALLIDA, concurrency = "2-3")
    public void manejarFallida(EventoTransferencia evento) {
        log.warn("[NOTIFICACION] Transferencia {} fallida: {}",
                evento.getTransaccionId(), evento.getMotivo());
    }

    @JmsListener(destination = JmsConfig.COLA_TRANSFERENCIA_REVERTIDA, concurrency = "2-3")
    public void manejarRevertida(EventoTransferencia evento) {
        log.warn("[NOTIFICACION] Transferencia {} revertida (compensada): {}",
                evento.getTransaccionId(), evento.getMotivo());
    }
}
