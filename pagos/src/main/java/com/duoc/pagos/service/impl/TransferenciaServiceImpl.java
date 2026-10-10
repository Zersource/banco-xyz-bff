package com.duoc.pagos.service.impl;

import com.duoc.pagos.dto.TransferenciaRequestDTO;
import com.duoc.pagos.dto.TransferenciaResponseDTO;
import com.duoc.pagos.saga.evento.EventoTransferencia;
import com.duoc.pagos.saga.evento.TransferenciaEventoProductor;
import com.duoc.pagos.model.EstadoTransaccion;
import com.duoc.pagos.model.Transaccion;
import com.duoc.pagos.repository.TransaccionRepository;
import com.duoc.pagos.service.TransferenciaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

@Service
public class TransferenciaServiceImpl implements TransferenciaService {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaServiceImpl.class);

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private TransferenciaEventoProductor eventoProductor;

    @Override
    public TransferenciaResponseDTO iniciarTransferencia(TransferenciaRequestDTO request) {
        validarSolicitud(request);

        Transaccion transaccion = new Transaccion(
                request.getCuentaOrigenId(),
                request.getCuentaDestinoId(),
                request.getMonto()
        );
        transaccion = transaccionRepository.save(transaccion);
        log.info("[tx={}] Transferencia recibida (PENDIENTE)", transaccion.getId());

        EventoTransferencia evento = new EventoTransferencia(
                transaccion.getId(),
                transaccion.getCuentaOrigenId(),
                transaccion.getCuentaDestinoId(),
                transaccion.getMonto(),
                null,
                null
        );
        eventoProductor.publicarTransferenciaIniciada(evento);

        return new TransferenciaResponseDTO(
                transaccion.getId(),
                transaccion.getEstado(),
                "Transferencia recibida, procesando de forma asincrona"
        );
    }

    @Override
    public TransferenciaResponseDTO consultarEstado(String transaccionId) {
        Transaccion transaccion = transaccionRepository.findById(transaccionId)
                .orElseThrow(() -> new NoSuchElementException("Transaccion no encontrada: " + transaccionId));

        return new TransferenciaResponseDTO(
                transaccion.getId(),
                transaccion.getEstado(),
                transaccion.getMotivoFallo()
        );
    }

    /**
     * Validacion de entrada de la solicitud, antes de crear ningun
     * registro. Mapeada a 400 por TransferenciaController.
     */
    private void validarSolicitud(TransferenciaRequestDTO request) {
        if (request.getCuentaOrigenId() == null || request.getCuentaDestinoId() == null) {
            throw new IllegalArgumentException("cuentaOrigenId y cuentaDestinoId son obligatorios");
        }
        if (request.getMonto() == null || request.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
        if (request.getCuentaOrigenId().equals(request.getCuentaDestinoId())) {
            throw new IllegalArgumentException("cuentaOrigenId y cuentaDestinoId no pueden ser la misma cuenta");
        }
    }
}
