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

import java.math.BigDecimal;

/**
 * Paso 1 de la saga: consume TRANSFERENCIA_INICIADA e intenta debitar la
 * cuenta origen. Si el saldo alcanza, publica DEBITO_REALIZADO (avanza a
 * paso 2). Si no alcanza, publica DEBITO_FALLIDO y ahi termina la saga
 * (no hay nada que compensar porque no se toco ninguna cuenta).
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

        BigDecimal saldoActual = cuentaSaldoPuerto.obtenerSaldo(evento.getCuentaOrigenId());

        if (saldoActual.compareTo(evento.getMonto()) < 0) {
            transaccion.setEstado(EstadoTransaccion.FALLIDA);
            transaccion.setMotivoFallo("Fondos insuficientes en cuenta origen");
            transaccionRepository.save(transaccion);

            evento.setMotivo("Fondos insuficientes en cuenta origen");
            eventoProductor.publicarDebitoFallido(evento);
            log.warn("Debito fallido para transaccion {}: fondos insuficientes", evento.getTransaccionId());
            return;
        }

        cuentaSaldoPuerto.actualizarSaldo(evento.getCuentaOrigenId(), saldoActual.subtract(evento.getMonto()));

        transaccion.setEstado(EstadoTransaccion.DEBITO_OK);
        transaccionRepository.save(transaccion);

        eventoProductor.publicarDebitoRealizado(evento);
        log.info("Debito realizado para transaccion {}", evento.getTransaccionId());
    }
}
