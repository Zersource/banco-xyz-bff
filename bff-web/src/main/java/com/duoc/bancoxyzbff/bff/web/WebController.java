package com.duoc.bancoxyzbff.bff.web;

import com.duoc.bancoxyzbff.bff.web.dto.CuentaWebDTO;
import com.duoc.bancoxyzbff.model.Cuenta;
import com.duoc.bancoxyzbff.model.Transaccion;
import com.duoc.bancoxyzbff.service.CuentaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * BFF para el canal Web: entrega la informacion completa de cada cuenta,
 * pensado para una interfaz de escritorio con espacio para mostrar
 * todo el detalle disponible.
 */
@RestController
@RequestMapping("/api/web")
public class WebController {

    @Autowired
    private CuentaService cuentaService;

    @GetMapping("/cuentas")
    public List<CuentaWebDTO> listarCuentas() {
        List<Cuenta> cuentas = cuentaService.obtenerTodasLasCuentas();
        return cuentas.stream()
                .map(cuenta -> new CuentaWebDTO(cuenta, cuentaService.obtenerMovimientosDeCuenta(cuenta.getCuentaId())))
                .collect(Collectors.toList());
    }

    @GetMapping("/cuentas/{cuentaId}")
    public CuentaWebDTO obtenerCuenta(@PathVariable Long cuentaId) {
        Cuenta cuenta = cuentaService.obtenerCuentaPorId(cuentaId);
        return new CuentaWebDTO(cuenta, cuentaService.obtenerMovimientosDeCuenta(cuentaId));
    }

    @GetMapping("/transacciones")
    public List<Transaccion> listarTransaccionesGenerales() {
        return cuentaService.obtenerTransaccionesGenerales();
    }
}
