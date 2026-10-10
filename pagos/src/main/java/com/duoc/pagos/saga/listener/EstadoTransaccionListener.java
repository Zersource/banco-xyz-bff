package com.duoc.pagos.saga.listener;

import com.duoc.pagos.model.EstadoTransaccion;
import com.duoc.pagos.model.Transaccion;
import com.duoc.pagos.repository.TransaccionRepository;
import com.duoc.pagos.saga.config.KafkaTopicsConfig;
import com.duoc.pagos.saga.evento.EventoTransferencia;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Cierra el ciclo de la saga: consume el resultado que publica cuentas
 * (completada, debito-fallido o revertida) y deja la transferencia en su
 * estado final. Es la unica parte que escribe el estado en pagos; los saldos
 * son de cuentas. Un mensaje reentregado no cambia nada porque solo se
 * actualiza una transferencia que sigue PENDIENTE.
 */
@Component
public class EstadoTransaccionListener {

    private static final Logger log = LoggerFactory.getLogger(EstadoTransaccionListener.class);

    @Autowired
    private TransaccionRepository transaccionRepository;

    @KafkaListener(topics = KafkaTopicsConfig.COMPLETADA, groupId = KafkaTopicsConfig.GRUPO)
    public void manejarCompletada(ConsumerRecord<String, EventoTransferencia> registro) {
        EventoTransferencia evento = registro.value();
        registrarRecepcion(registro);
        if (cerrar(evento, EstadoTransaccion.COMPLETADA, null)) {
            log.info("[tx={}] Transferencia completada: {} -> {} por {}", evento.getTransaccionId(),
                    evento.getCuentaOrigenId(), evento.getCuentaDestinoId(), evento.getMonto());
        }
    }

    @KafkaListener(topics = KafkaTopicsConfig.DEBITO_FALLIDO, groupId = KafkaTopicsConfig.GRUPO)
    public void manejarFallida(ConsumerRecord<String, EventoTransferencia> registro) {
        EventoTransferencia evento = registro.value();
        registrarRecepcion(registro);
        if (cerrar(evento, EstadoTransaccion.FALLIDA, evento.getMotivo())) {
            log.warn("[tx={}] Transferencia fallida: {}", evento.getTransaccionId(), evento.getMotivo());
        }
    }

    @KafkaListener(topics = KafkaTopicsConfig.REVERTIDA, groupId = KafkaTopicsConfig.GRUPO)
    public void manejarRevertida(ConsumerRecord<String, EventoTransferencia> registro) {
        EventoTransferencia evento = registro.value();
        registrarRecepcion(registro);
        if (cerrar(evento, EstadoTransaccion.REVERTIDA, evento.getMotivo())) {
            log.warn("[tx={}] Transferencia revertida (compensada): {}", evento.getTransaccionId(), evento.getMotivo());
        }
    }

    private void registrarRecepcion(ConsumerRecord<String, EventoTransferencia> registro) {
        log.info("[tx={}] <- {} (particion {}, offset {})",
                registro.value().getTransaccionId(), registro.topic(), registro.partition(), registro.offset());
    }

    private boolean cerrar(EventoTransferencia evento, EstadoTransaccion estadoFinal, String motivo) {
        Transaccion transaccion = transaccionRepository.findById(evento.getTransaccionId()).orElse(null);
        if (transaccion == null) {
            log.error("[tx={}] Resultado recibido para una transferencia desconocida", evento.getTransaccionId());
            return false;
        }
        if (transaccion.getEstado() != EstadoTransaccion.PENDIENTE) {
            log.warn("[tx={}] Resultado {} ignorado: estado actual {} (probable reentrega)",
                    evento.getTransaccionId(), estadoFinal, transaccion.getEstado());
            return false;
        }
        transaccion.setEstado(estadoFinal);
        transaccion.setMotivoFallo(motivo);
        transaccion.setFechaActualizacion(LocalDateTime.now());
        transaccionRepository.save(transaccion);
        return true;
    }
}
