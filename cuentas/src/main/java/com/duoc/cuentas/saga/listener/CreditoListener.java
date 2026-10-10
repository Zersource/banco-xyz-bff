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
 * Paso 2 de la saga: consume transferencia.debito-realizado e intenta
 * acreditar la cuenta destino de forma atomica (CuentaSaldoPuerto.acreditar).
 * Si funciona, publica transferencia.completada (fin feliz). Si la cuenta
 * destino no existe / falla, publica credito-fallido para que
 * CompensacionListener revierta el debito ya aplicado.
 */
@Component
public class CreditoListener {

    private static final Logger log = LoggerFactory.getLogger(CreditoListener.class);

    @Autowired
    private CuentaSaldoPuerto cuentaSaldoPuerto;

    @Autowired
    private EstadoSagaRepository estadoSagaRepository;

    @Autowired
    private TransferenciaEventoProductor eventoProductor;

    @KafkaListener(topics = KafkaTopicsConfig.DEBITO_REALIZADO, groupId = KafkaTopicsConfig.GRUPO)
    public void manejarDebitoRealizado(ConsumerRecord<String, EventoTransferencia> registro) {
        EventoTransferencia evento = registro.value();
        String id = evento.getTransaccionId();
        log.info("[tx={}] <- {} (particion {}, offset {}): acreditar {} a la cuenta {}",
                id, registro.topic(), registro.partition(), registro.offset(),
                evento.getMonto(), evento.getCuentaDestinoId());

        // Guard de idempotencia: si Kafka reentrega este mensaje y la
        // transferencia ya avanzo mas alla de DEBITO_OK, no volver a acreditar.
        EstadoSaga estado = estadoSagaRepository.buscar(id).orElse(null);
        if (estado != EstadoSaga.DEBITO_OK) {
            log.warn("[tx={}] Mensaje de credito ignorado: estado actual {} (no DEBITO_OK, probable reentrega)", id, estado);
            return;
        }

        try {
            cuentaSaldoPuerto.acreditar(evento.getCuentaDestinoId(), evento.getMonto());
        } catch (RuntimeException ex) {
            log.error("[tx={}] Credito fallido -> {}: {}", id, KafkaTopicsConfig.CREDITO_FALLIDO, ex.getMessage());
            evento.setMotivo("Error al acreditar cuenta destino: " + ex.getMessage());
            eventoProductor.publicarCreditoFallido(evento);
            return;
        }

        estadoSagaRepository.cambiar(id, EstadoSaga.DEBITO_OK, EstadoSaga.COMPLETADA);
        eventoProductor.publicarTransferenciaCompletada(evento);
        log.info("[tx={}] Credito realizado, transferencia completada -> {}", id, KafkaTopicsConfig.COMPLETADA);
    }
}
