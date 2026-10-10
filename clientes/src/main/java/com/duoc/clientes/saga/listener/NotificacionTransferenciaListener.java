package com.duoc.clientes.saga.listener;

import com.duoc.clientes.model.Cliente;
import com.duoc.clientes.repository.ClienteRepository;
import com.duoc.clientes.saga.config.KafkaTopicsConfig;
import com.duoc.clientes.saga.evento.EventoTransferencia;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Segundo consumidor de transferencia.completada (el primero es pagos, que
 * actualiza el estado): clientes avisa a los titulares de la cuenta origen y
 * de la cuenta destino. La notificacion se registra en el log.
 */
@Component
public class NotificacionTransferenciaListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionTransferenciaListener.class);

    @Autowired
    private ClienteRepository clienteRepository;

    @KafkaListener(topics = KafkaTopicsConfig.COMPLETADA, groupId = KafkaTopicsConfig.GRUPO)
    public void notificarTransferenciaCompletada(ConsumerRecord<Long, EventoTransferencia> registro) {
        EventoTransferencia evento = registro.value();
        Long id = evento.getTransaccionId();
        log.info("[tx={}] <- {} (particion {}, offset {})", id, registro.topic(), registro.partition(), registro.offset());
        log.info("[tx={}] [NOTIFICACION] {}: se debito {} de tu cuenta {}", id,
                nombre(evento.getCuentaOrigenId()), evento.getMonto(), evento.getCuentaOrigenId());
        log.info("[tx={}] [NOTIFICACION] {}: se acredito {} en tu cuenta {}", id,
                nombre(evento.getCuentaDestinoId()), evento.getMonto(), evento.getCuentaDestinoId());
    }

    private String nombre(Long cuentaId) {
        return clienteRepository.buscarPorCuentaId(cuentaId).map(Cliente::getNombre).orElse("Cliente desconocido");
    }
}
