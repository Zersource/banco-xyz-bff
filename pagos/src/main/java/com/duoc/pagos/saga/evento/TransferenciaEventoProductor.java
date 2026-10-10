package com.duoc.pagos.saga.evento;

import com.duoc.pagos.saga.config.KafkaTopicsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica el evento que inicia la saga. La key del mensaje es el
 * transaccionId: asi todos los eventos de una misma transferencia caen en la
 * misma particion y se procesan en orden.
 */
@Component
public class TransferenciaEventoProductor {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaEventoProductor.class);

    @Autowired
    private KafkaTemplate<Long, EventoTransferencia> kafkaTemplate;

    public void publicarTransferenciaIniciada(EventoTransferencia evento) {
        evento.setTipoEvento(TipoEventoTransferencia.TRANSFERENCIA_INICIADA);
        kafkaTemplate.send(KafkaTopicsConfig.TRANSFERENCIA_INICIADA, evento.getTransaccionId(), evento);
        log.info("[tx={}] -> {}: transferir {} de la cuenta {} a la cuenta {}",
                evento.getTransaccionId(), KafkaTopicsConfig.TRANSFERENCIA_INICIADA,
                evento.getMonto(), evento.getCuentaOrigenId(), evento.getCuentaDestinoId());
    }
}
