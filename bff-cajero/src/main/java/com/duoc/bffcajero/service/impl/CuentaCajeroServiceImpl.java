package com.duoc.bffcajero.service.impl;

import com.duoc.bffcajero.bff.cajero.dto.RetiroResponseDTO;
import com.duoc.bffcajero.bff.cajero.dto.SaldoDTO;
import com.duoc.bffcajero.client.CuentasClient;
import com.duoc.bffcajero.service.CuentaCajeroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CuentaCajeroServiceImpl implements CuentaCajeroService {

    @Autowired
    private CuentasClient cuentasClient;

    @Override
    public SaldoDTO consultarSaldo(Long cuentaId) {
        Double saldo = cuentasClient.consultarSaldo(cuentaId);
        return new SaldoDTO(cuentaId, saldo);
    }

    @Override
    public RetiroResponseDTO realizarRetiro(Long cuentaId, Double monto) {
        Double saldoResultante = cuentasClient.retirar(cuentaId, monto);
        return new RetiroResponseDTO(cuentaId, monto, saldoResultante);
    }
}
