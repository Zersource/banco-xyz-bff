package com.duoc.bancoxyzbff.transferencia.service.impl;

import com.duoc.bancoxyzbff.client.PagosClient;
import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaRequestDTO;
import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaResponseDTO;
import com.duoc.bancoxyzbff.transferencia.service.TransferenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * bff-web ya no ejecuta la saga: delega la transferencia en el microservicio
 * pagos (via PagosClient, con Circuit Breaker).
 */
@Service
public class TransferenciaServiceImpl implements TransferenciaService {

    @Autowired
    private PagosClient pagosClient;

    @Override
    public TransferenciaResponseDTO iniciarTransferencia(TransferenciaRequestDTO request) {
        return pagosClient.iniciarTransferencia(request);
    }

    @Override
    public TransferenciaResponseDTO consultarEstado(Long transaccionId) {
        return pagosClient.consultarEstado(transaccionId);
    }
}
