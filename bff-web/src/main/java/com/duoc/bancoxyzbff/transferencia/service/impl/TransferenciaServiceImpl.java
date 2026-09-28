package com.duoc.bancoxyzbff.transferencia.service.impl;

import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaRequestDTO;
import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaResponseDTO;
import com.duoc.bancoxyzbff.transferencia.evento.EventoTransferencia;
import com.duoc.bancoxyzbff.transferencia.evento.TransferenciaEventoProductor;
import com.duoc.bancoxyzbff.transferencia.model.EstadoTransaccion;
import com.duoc.bancoxyzbff.transferencia.model.Transaccion;
import com.duoc.bancoxyzbff.transferencia.repository.TransaccionRepository;
import com.duoc.bancoxyzbff.transferencia.service.TransferenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class TransferenciaServiceImpl implements TransferenciaService {

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private TransferenciaEventoProductor eventoProductor;

    @Override
    public TransferenciaResponseDTO iniciarTransferencia(TransferenciaRequestDTO request) {
        Transaccion transaccion = new Transaccion(
                request.getCuentaOrigenId(),
                request.getCuentaDestinoId(),
                request.getMonto()
        );
        transaccion = transaccionRepository.save(transaccion);

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
    public TransferenciaResponseDTO consultarEstado(Long transaccionId) {
        Transaccion transaccion = transaccionRepository.findById(transaccionId)
                .orElseThrow(() -> new NoSuchElementException("Transaccion no encontrada: " + transaccionId));

        return new TransferenciaResponseDTO(
                transaccion.getId(),
                transaccion.getEstado(),
                transaccion.getMotivoFallo()
        );
    }
}
