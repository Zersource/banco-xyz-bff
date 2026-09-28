package com.duoc.bancoxyzbff.transferencia.listener;

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
 * Paso 2 de la saga: consume DEBITO_REALIZADO e intenta acreditar la
 * cuenta destino de forma atomica (CuentaSaldoPuerto.acreditar). Si
 * funciona, publica TRANSFERENCIA_COMPLETADA (fin feliz). Si la cuenta
 * destino no existe / falla, publica CREDITO_FALLIDO para que
 * CompensacionListener revierta el debito ya aplicado.
 */
@Component
public class CreditoListener {

    private static final Logger log = LoggerFactory.getLogger(CreditoListener.class);

    @Autowired
    private CuentaSaldoPuerto cuentaSaldoPuerto;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private TransferenciaEventoProductor eventoProductor;

    @JmsListener(destination = JmsConfig.COLA_DEBITO_REALIZADO, concurrency = "3-5")
    public void manejarDebitoRealizado(EventoTransferencia evento) {
        log.info("Procesando credito para transaccion {}", evento.getTransaccionId());

        Transaccion transaccion = transaccionRepository.findById(evento.getTransaccionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Transaccion no encontrada: " + evento.getTransaccionId()));

        // Guard de idempotencia: si JMS reentrega este mensaje y la
        // transaccion ya avanzo mas alla de DEBITO_OK, no volver a acreditar.
        if (transaccion.getEstado() != EstadoTransaccion.DEBITO_OK) {
            log.warn("Mensaje de credito ignorado para transaccion {}: estado actual {} (no DEBITO_OK, probable reentrega de JMS)",
                    evento.getTransaccionId(), transaccion.getEstado());
            return;
        }

        try {
            cuentaSaldoPuerto.acreditar(evento.getCuentaDestinoId(), evento.getMonto());

            transaccion.setEstado(EstadoTransaccion.COMPLETADA);
            transaccionRepository.save(transaccion);

            eventoProductor.publicarTransferenciaCompletada(evento);
            log.info("Transferencia {} completada", evento.getTransaccionId());

        } catch (RuntimeException ex) {
            log.error("Credito fallido para transaccion {}: {}", evento.getTransaccionId(), ex.getMessage());
            evento.setMotivo("Error al acreditar cuenta destino: " + ex.getMessage());
            eventoProductor.publicarCreditoFallido(evento);
        }
    }
}
