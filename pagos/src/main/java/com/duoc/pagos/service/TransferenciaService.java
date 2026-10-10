package com.duoc.pagos.service;

import com.duoc.pagos.dto.TransferenciaRequestDTO;
import com.duoc.pagos.dto.TransferenciaResponseDTO;

public interface TransferenciaService {

    /**
     * Crea el registro de transaccion en estado PENDIENTE y publica el
     * evento TRANSFERENCIA_INICIADA. No debita ni acredita nada de forma
     * sincrona: eso lo hacen los listeners de la saga.
     */
    TransferenciaResponseDTO iniciarTransferencia(TransferenciaRequestDTO request);

    /**
     * Consulta el estado actual de una transaccion (para que
     * bff-movil/bff-cajero hagan polling del resultado de la saga).
     */
    TransferenciaResponseDTO consultarEstado(Long transaccionId);
}
