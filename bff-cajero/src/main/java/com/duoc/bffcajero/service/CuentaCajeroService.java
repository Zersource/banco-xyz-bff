package com.duoc.bffcajero.service;

import com.duoc.bffcajero.bff.cajero.dto.RetiroResponseDTO;
import com.duoc.bffcajero.bff.cajero.dto.SaldoDTO;

public interface CuentaCajeroService {
    SaldoDTO consultarSaldo(Long cuentaId);
    RetiroResponseDTO realizarRetiro(Long cuentaId, Double monto);
}
