package com.duoc.cuentas.saga.evento;

import com.duoc.cuentas.saga.config.KafkaTopicsConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica los eventos que genera cuentas en cada paso de la saga. La key de
 * cada mensaje es el transaccionId: asi todos los eventos de una misma
 * transferencia caen en la misma particion y se procesan en orden.
 */
@Component
public class TransferenciaEventoProductor {

    @Autowired
    private KafkaTemplate<Long, EventoTransferencia> kafkaTemplate;

    public void publicarDebitoRealizado(EventoTransferencia evento) {
        enviar(KafkaTopicsConfig.DEBITO_REALIZADO, TipoEventoTransferencia.DEBITO_REALIZADO, evento);
    }

    public void publicarDebitoFallido(EventoTransferencia evento) {
        enviar(KafkaTopicsConfig.DEBITO_FALLIDO, TipoEventoTransferencia.DEBITO_FALLIDO, evento);
    }

    public void publicarTransferenciaCompletada(EventoTransferencia evento) {
        enviar(KafkaTopicsConfig.COMPLETADA, TipoEventoTransferencia.TRANSFERENCIA_COMPLETADA, evento);
    }

    public void publicarCreditoFallido(EventoTransferencia evento) {
        enviar(KafkaTopicsConfig.CREDITO_FALLIDO, TipoEventoTransferencia.CREDITO_FALLIDO, evento);
    }

    public void publicarTransferenciaRevertida(EventoTransferencia evento) {
        enviar(KafkaTopicsConfig.REVERTIDA, TipoEventoTransferencia.TRANSFERENCIA_REVERTIDA, evento);
    }

    private void enviar(String topic, TipoEventoTransferencia tipo, EventoTransferencia evento) {
        evento.setTipoEvento(tipo);
        kafkaTemplate.send(topic, evento.getTransaccionId(), evento);
    }
}
