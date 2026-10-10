package com.duoc.bancoxyzbff.transferencia.service;

import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaRequestDTO;
import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaResponseDTO;

public interface TransferenciaService {

    TransferenciaResponseDTO iniciarTransferencia(TransferenciaRequestDTO request);

    TransferenciaResponseDTO consultarEstado(String transaccionId);
}
