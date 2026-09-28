package com.duoc.bancoxyzbff.transferencia.evento;

import com.duoc.bancoxyzbff.transferencia.config.JmsConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * Productor de eventos: encapsula el JmsTemplate y sabe a que cola
 * corresponde cada tipo de evento de la saga. Los listeners lo usan
 * para publicar el siguiente paso (o la compensacion).
 */
@Component
public class TransferenciaEventoProductor {

    @Autowired
    private JmsTemplate jmsTemplate;

    public void publicarTransferenciaIniciada(EventoTransferencia evento) {
        evento.setTipoEvento(TipoEventoTransferencia.TRANSFERENCIA_INICIADA);
        jmsTemplate.convertAndSend(JmsConfig.COLA_TRANSFERENCIA_INICIADA, evento);
    }

    public void publicarDebitoRealizado(EventoTransferencia evento) {
        evento.setTipoEvento(TipoEventoTransferencia.DEBITO_REALIZADO);
        jmsTemplate.convertAndSend(JmsConfig.COLA_DEBITO_REALIZADO, evento);
    }

    public void publicarDebitoFallido(EventoTransferencia evento) {
        evento.setTipoEvento(TipoEventoTransferencia.DEBITO_FALLIDO);
        jmsTemplate.convertAndSend(JmsConfig.COLA_TRANSFERENCIA_FALLIDA, evento);
    }

    public void publicarTransferenciaCompletada(EventoTransferencia evento) {
        evento.setTipoEvento(TipoEventoTransferencia.TRANSFERENCIA_COMPLETADA);
        jmsTemplate.convertAndSend(JmsConfig.COLA_TRANSFERENCIA_COMPLETADA, evento);
    }

    public void publicarCreditoFallido(EventoTransferencia evento) {
        evento.setTipoEvento(TipoEventoTransferencia.CREDITO_FALLIDO);
        jmsTemplate.convertAndSend(JmsConfig.COLA_TRANSFERENCIA_COMPENSACION, evento);
    }

    public void publicarTransferenciaRevertida(EventoTransferencia evento) {
        evento.setTipoEvento(TipoEventoTransferencia.TRANSFERENCIA_REVERTIDA);
        jmsTemplate.convertAndSend(JmsConfig.COLA_TRANSFERENCIA_REVERTIDA, evento);
    }
}
