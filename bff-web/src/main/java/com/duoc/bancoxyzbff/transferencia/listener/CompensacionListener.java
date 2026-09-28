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
 * Paso compensatorio de la saga: consume CREDITO_FALLIDO y revierte el
 * debito que ya se le habia aplicado a la cuenta origen (esto es lo que
 * hace a este flujo una Saga y no solo un pipeline de eventos: hay una
 * accion compensatoria explicita ante el fallo de un paso posterior).
 */
@Component
public class CompensacionListener {

    private static final Logger log = LoggerFactory.getLogger(CompensacionListener.class);

    @Autowired
    private CuentaSaldoPuerto cuentaSaldoPuerto;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private TransferenciaEventoProductor eventoProductor;

    @JmsListener(destination = JmsConfig.COLA_TRANSFERENCIA_COMPENSACION, concurrency = "3-5")
    public void manejarCreditoFallido(EventoTransferencia evento) {
        log.info("Compensando (revirtiendo debito) transaccion {}", evento.getTransaccionId());

        Transaccion transaccion = transaccionRepository.findById(evento.getTransaccionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Transaccion no encontrada: " + evento.getTransaccionId()));

        BigDecimal saldoOrigen = cuentaSaldoPuerto.obtenerSaldo(evento.getCuentaOrigenId());
        cuentaSaldoPuerto.actualizarSaldo(evento.getCuentaOrigenId(), saldoOrigen.add(evento.getMonto()));

        transaccion.setEstado(EstadoTransaccion.REVERTIDA);
        transaccionRepository.save(transaccion);

        eventoProductor.publicarTransferenciaRevertida(evento);
        log.info("Transaccion {} revertida (compensacion aplicada)", evento.getTransaccionId());
    }
}
