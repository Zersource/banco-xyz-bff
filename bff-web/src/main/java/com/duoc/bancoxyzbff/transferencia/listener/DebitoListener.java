package com.duoc.bancoxyzbff.transferencia.listener;

import com.duoc.bancoxyzbff.exception.CuentaNoEncontradaException;
import com.duoc.bancoxyzbff.transferencia.config.JmsConfig;
import com.duoc.bancoxyzbff.transferencia.evento.EventoTransferencia;
import com.duoc.bancoxyzbff.transferencia.evento.TransferenciaEventoProductor;
import com.duoc.bancoxyzbff.transferencia.model.EstadoTransaccion;
import com.duoc.bancoxyzbff.transferencia.model.Transaccion;
import com.duoc.bancoxyzbff.transferencia.repository.CuentaSaldoPuerto;
import com.duoc.bancoxyzbff.transferencia.repository.TransaccionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Paso 1 de la saga: consume TRANSFERENCIA_INICIADA e intenta debitar la
 * cuenta origen de forma atomica (CuentaSaldoPuerto.debitar). Si el saldo
 * alcanza, publica DEBITO_REALIZADO (avanza a paso 2). Si no alcanza, o si
 * la cuenta origen no existe, publica DEBITO_FALLIDO y ahi termina la saga
 * (no hay nada que compensar porque no se toco ninguna cuenta). Ninguna
 * excepcion se deja escapar del listener: si escapara, JMS reentregaria
 * el mensaje indefinidamente y la transaccion quedaria PENDIENTE para
 * siempre.
 */
@Component
public class DebitoListener {

    private static final Logger log = LoggerFactory.getLogger(DebitoListener.class);

    @Autowired
    private CuentaSaldoPuerto cuentaSaldoPuerto;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private TransferenciaEventoProductor eventoProductor;

    @JmsListener(destination = JmsConfig.COLA_TRANSFERENCIA_INICIADA, concurrency = "3-5")
    public void manejarTransferenciaIniciada(EventoTransferencia evento) {
        log.info("Procesando debito para transaccion {}", evento.getTransaccionId());

        Transaccion transaccion = transaccionRepository.findById(evento.getTransaccionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Transaccion no encontrada: " + evento.getTransaccionId()));

        // Guard de idempotencia: si JMS reentrega este mensaje y la
        // transaccion ya dejo de estar PENDIENTE, no volver a debitar.
        if (transaccion.getEstado() != EstadoTransaccion.PENDIENTE) {
            log.warn("Mensaje de debito ignorado para transaccion {}: estado actual {} (no PENDIENTE, probable reentrega de JMS)",
                    evento.getTransaccionId(), transaccion.getEstado());
            return;
        }

        boolean debitado;
        try {
            debitado = cuentaSaldoPuerto.debitar(evento.getCuentaOrigenId(), evento.getMonto());
        } catch (CuentaNoEncontradaException ex) {
            fallar(transaccion, evento, "Cuenta origen no encontrada: " + evento.getCuentaOrigenId());
            return;
        }

        if (!debitado) {
            fallar(transaccion, evento, "Fondos insuficientes en cuenta origen");
            return;
        }

        transaccion.setEstado(EstadoTransaccion.DEBITO_OK);
        transaccionRepository.save(transaccion);

        eventoProductor.publicarDebitoRealizado(evento);
        log.info("Debito realizado para transaccion {}", evento.getTransaccionId());
    }

    private void fallar(Transaccion transaccion, EventoTransferencia evento, String motivo) {
        transaccion.setEstado(EstadoTransaccion.FALLIDA);
        transaccion.setMotivoFallo(motivo);
        transaccionRepository.save(transaccion);

        evento.setMotivo(motivo);
        eventoProductor.publicarDebitoFallido(evento);
        log.warn("Debito fallido para transaccion {}: {}", evento.getTransaccionId(), motivo);
    }
}
