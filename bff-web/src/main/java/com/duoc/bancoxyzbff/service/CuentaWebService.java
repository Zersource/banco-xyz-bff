package com.duoc.bancoxyzbff.service;

import com.duoc.bancoxyzbff.bff.web.dto.CuentaWebDTO;
import com.duoc.bancoxyzbff.dto.TransaccionDTO;

import java.util.List;

/**
 * Arma las respuestas del canal Web juntando lo que entregan los
 * microservicios cuentas y clientes.
 */
public interface CuentaWebService {

    List<CuentaWebDTO> obtenerCuentas();

    CuentaWebDTO obtenerCuenta(Long cuentaId);

    List<TransaccionDTO> obtenerTransaccionesGenerales();
}
