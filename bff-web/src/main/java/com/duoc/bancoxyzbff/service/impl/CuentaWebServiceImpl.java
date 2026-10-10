package com.duoc.bancoxyzbff.service.impl;

import com.duoc.bancoxyzbff.bff.web.dto.CuentaWebDTO;
import com.duoc.bancoxyzbff.client.ClientesClient;
import com.duoc.bancoxyzbff.client.CuentasClient;
import com.duoc.bancoxyzbff.dto.ClienteDTO;
import com.duoc.bancoxyzbff.dto.CuentaDTO;
import com.duoc.bancoxyzbff.dto.MovimientoDTO;
import com.duoc.bancoxyzbff.dto.TransaccionDTO;
import com.duoc.bancoxyzbff.service.CuentaWebService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CuentaWebServiceImpl implements CuentaWebService {

    @Autowired
    private CuentasClient cuentasClient;

    @Autowired
    private ClientesClient clientesClient;

    @Override
    public List<CuentaWebDTO> obtenerCuentas() {
        List<CuentaDTO> cuentas = cuentasClient.listarCuentas();
        Map<Long, ClienteDTO> clientes = clientesClient.listarClientes().stream()
                .collect(Collectors.toMap(ClienteDTO::getCuentaId, Function.identity()));
        Map<Long, List<MovimientoDTO>> movimientos = cuentasClient.listarMovimientos().stream()
                .collect(Collectors.groupingBy(MovimientoDTO::getCuentaId));

        return cuentas.stream()
                .map(cuenta -> new CuentaWebDTO(cuenta, clientes.get(cuenta.getCuentaId()),
                        movimientos.getOrDefault(cuenta.getCuentaId(), List.of())))
                .collect(Collectors.toList());
    }

    @Override
    public CuentaWebDTO obtenerCuenta(Long cuentaId) {
        CuentaDTO cuenta = cuentasClient.obtenerCuenta(cuentaId);
        ClienteDTO cliente = clientesClient.obtenerCliente(cuentaId);
        List<MovimientoDTO> movimientos = cuentasClient.listarMovimientosDeCuenta(cuentaId);
        return new CuentaWebDTO(cuenta, cliente, movimientos);
    }

    @Override
    public List<TransaccionDTO> obtenerTransaccionesGenerales() {
        return cuentasClient.listarTransacciones();
    }
}
