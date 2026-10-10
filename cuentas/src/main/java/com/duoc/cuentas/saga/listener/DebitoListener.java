package com.duoc.cuentas.saga.listener;

import com.duoc.cuentas.exception.CuentaNoEncontradaException;
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
 * Paso 1 de la saga: consume transferencia.iniciada (publicado por pagos) e
 * intenta debitar la cuenta origen de forma atomica (CuentaSaldoPuerto.debitar).
 * Si el saldo alcanza, publica debito-realizado (avanza al paso 2). Si no
 * alcanza, o si la cuenta origen no existe, publica debito-fallido y ahi
 * termina la saga (no hay nada que compensar porque no se toco ninguna
 * cuenta). Ninguna excepcion se deja escapar del listener: si escapara,
 * Kafka reintentaria el mensaje y la transferencia quedaria pendiente.
 */
@Component
public class DebitoListener {

    private static final Logger log = LoggerFactory.getLogger(DebitoListener.class);

    @Autowired
    private CuentaSaldoPuerto cuentaSaldoPuerto;

    @Autowired
    private EstadoSagaRepository estadoSagaRepository;

    @Autowired
    private TransferenciaEventoProductor eventoProductor;

    @KafkaListener(topics = KafkaTopicsConfig.TRANSFERENCIA_INICIADA, groupId = KafkaTopicsConfig.GRUPO)
    public void manejarTransferenciaIniciada(ConsumerRecord<String, EventoTransferencia> registro) {
        EventoTransferencia evento = registro.value();
        String id = evento.getTransaccionId();
        log.info("[tx={}] <- {} (particion {}, offset {}): debitar {} de la cuenta {}",
                id, registro.topic(), registro.partition(), registro.offset(),
                evento.getMonto(), evento.getCuentaOrigenId());

        // Guard de idempotencia: si Kafka reentrega este mensaje, la transferencia
        // ya esta registrada y no se vuelve a debitar.
        if (!estadoSagaRepository.registrarPendiente(id)) {
            log.warn("[tx={}] Mensaje de debito ignorado: la transferencia ya fue procesada (estado {}, probable reentrega)",
                    id, estadoSagaRepository.buscar(id).orElse(null));
            return;
        }

        boolean debitado;
        try {
            debitado = cuentaSaldoPuerto.debitar(evento.getCuentaOrigenId(), evento.getMonto());
        } catch (CuentaNoEncontradaException ex) {
            fallar(evento, "Cuenta origen no encontrada: " + evento.getCuentaOrigenId());
            return;
        }

        if (!debitado) {
            fallar(evento, "Fondos insuficientes en cuenta origen");
            return;
        }

        estadoSagaRepository.cambiar(id, EstadoSaga.PENDIENTE, EstadoSaga.DEBITO_OK);
        eventoProductor.publicarDebitoRealizado(evento);
        log.info("[tx={}] Debito realizado -> {}", id, KafkaTopicsConfig.DEBITO_REALIZADO);
    }

    private void fallar(EventoTransferencia evento, String motivo) {
        String id = evento.getTransaccionId();
        estadoSagaRepository.cambiar(id, EstadoSaga.PENDIENTE, EstadoSaga.FALLIDA);
        evento.setMotivo(motivo);
        eventoProductor.publicarDebitoFallido(evento);
        log.warn("[tx={}] Debito fallido -> {}: {}", id, KafkaTopicsConfig.DEBITO_FALLIDO, motivo);
    }
}
