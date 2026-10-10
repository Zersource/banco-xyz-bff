package com.duoc.bffmovil.service.impl;

import com.duoc.bffmovil.bff.movil.dto.CuentaMovilDTO;
import com.duoc.bffmovil.client.ClientesClient;
import com.duoc.bffmovil.client.CuentasClient;
import com.duoc.bffmovil.dto.ClienteDTO;
import com.duoc.bffmovil.dto.CuentaDTO;
import com.duoc.bffmovil.dto.TransaccionDTO;
import com.duoc.bffmovil.service.CuentaMovilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CuentaMovilServiceImpl implements CuentaMovilService {

    private static final int CANTIDAD_ULTIMAS_TRANSACCIONES = 3;

    @Autowired
    private CuentasClient cuentasClient;

    @Autowired
    private ClientesClient clientesClient;

    @Override
    public CuentaMovilDTO obtenerCuenta(Long cuentaId) {
        CuentaDTO cuenta = cuentasClient.obtenerCuenta(cuentaId);
        List<TransaccionDTO> ultimasTransacciones = cuentasClient.obtenerUltimasTransacciones(CANTIDAD_ULTIMAS_TRANSACCIONES);
        ClienteDTO cliente = clientesClient.obtenerCliente(cuentaId);
        return new CuentaMovilDTO(cuenta, cliente, ultimasTransacciones);
    }
}
