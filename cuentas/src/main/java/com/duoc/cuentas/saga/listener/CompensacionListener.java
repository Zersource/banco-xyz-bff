package com.duoc.cuentas.saga.listener;

import com.duoc.cuentas.repository.CuentaSaldoPuerto;
import com.duoc.cuentas.saga.config.KafkaTopicsConfig;
import com.duoc.cuentas.saga.evento.EventoTransferencia;
import com.duoc.cuentas.saga.evento.TransferenciaEventoProductor;
import com.duoc.cuentas.saga.repository.EstadoSaga;
import com.duoc.cuentas.saga.repository.EstadoSagaRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Paso compensatorio de la saga: consume transferencia.credito-fallido y
 * revierte el debito que ya se le habia aplicado a la cuenta origen (esto es
 * lo que hace a este flujo una Saga y no solo un pipeline de eventos: hay
 * una accion compensatoria explicita ante el fallo de un paso posterior).
 * Usa CuentaSaldoPuerto.acreditar (atomico) para devolver la plata.
 */
@Component
public class CompensacionListener {

    private static final Logger log = LoggerFactory.getLogger(CompensacionListener.class);

    @Autowired
    private CuentaSaldoPuerto cuentaSaldoPuerto;

    @Autowired
    private EstadoSagaRepository estadoSagaRepository;

    @Autowired
    private TransferenciaEventoProductor eventoProductor;

    @KafkaListener(topics = KafkaTopicsConfig.CREDITO_FALLIDO, groupId = KafkaTopicsConfig.GRUPO)
    public void manejarCreditoFallido(ConsumerRecord<Long, EventoTransferencia> registro) {
        EventoTransferencia evento = registro.value();
        Long id = evento.getTransaccionId();
        log.info("[tx={}] <- {} (particion {}, offset {}): compensar, devolver {} a la cuenta {}",
                id, registro.topic(), registro.partition(), registro.offset(),
                evento.getMonto(), evento.getCuentaOrigenId());

        // Guard de idempotencia: la transferencia debe seguir en DEBITO_OK (ese
        // es el estado normal mientras espera compensacion, ver CreditoListener).
        // Si ya esta REVERTIDA, es una reentrega de Kafka.
        EstadoSaga estado = estadoSagaRepository.buscar(id).orElse(null);
        if (estado != EstadoSaga.DEBITO_OK) {
            log.warn("[tx={}] Mensaje de compensacion ignorado: estado actual {} (no DEBITO_OK, probable reentrega)", id, estado);
            return;
        }

        cuentaSaldoPuerto.acreditar(evento.getCuentaOrigenId(), evento.getMonto());
        estadoSagaRepository.cambiar(id, EstadoSaga.DEBITO_OK, EstadoSaga.REVERTIDA);
        eventoProductor.publicarTransferenciaRevertida(evento);
        log.info("[tx={}] Debito revertido (compensacion aplicada) -> {}", id, KafkaTopicsConfig.REVERTIDA);
    }
}
