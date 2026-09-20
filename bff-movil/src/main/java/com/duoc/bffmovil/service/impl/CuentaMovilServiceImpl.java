package com.duoc.bffmovil.service.impl;

import com.duoc.bffmovil.bff.movil.dto.CuentaMovilDTO;
import com.duoc.bffmovil.client.BffWebClient;
import com.duoc.bffmovil.dto.CuentaDTO;
import com.duoc.bffmovil.dto.TransaccionDTO;
import com.duoc.bffmovil.service.CuentaMovilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CuentaMovilServiceImpl implements CuentaMovilService {

    private static final int CANTIDAD_ULTIMOS_MOVIMIENTOS = 5;

    @Autowired
    private BffWebClient bffWebClient;

    @Override
    public CuentaMovilDTO obtenerCuenta(Long cuentaId) {
        CuentaDTO cuenta = bffWebClient.obtenerCuenta(cuentaId);
        List<TransaccionDTO> ultimasTransacciones = bffWebClient.obtenerUltimasTransacciones(CANTIDAD_ULTIMOS_MOVIMIENTOS);
        return new CuentaMovilDTO(cuenta, ultimasTransacciones);
    }
}
