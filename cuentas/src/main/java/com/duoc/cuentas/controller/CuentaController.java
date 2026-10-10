package com.duoc.cuentas.controller;

import com.duoc.cuentas.model.Cuenta;
import com.duoc.cuentas.model.MovimientoAnual;
import com.duoc.cuentas.service.CuentaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de cuentas, consumida por los 3 BFF a traves de Eureka. Las
 * lecturas aceptan el scope de cualquier canal; el debito (retiro del
 * cajero) y el credito tienen su propia regla en SeguridadConfig.
 */
@RestController
@RequestMapping("/cuentas")
public class CuentaController {

    @Autowired
    private CuentaService cuentaService;

    @GetMapping
    public List<Cuenta> listar() {
        return cuentaService.obtenerTodasLasCuentas();
    }

    @GetMapping("/{cuentaId}")
    public Cuenta obtener(@PathVariable Long cuentaId) {
        return cuentaService.obtenerCuentaPorId(cuentaId);
    }

    @GetMapping("/{cuentaId}/saldo")
    public Double consultarSaldo(@PathVariable Long cuentaId) {
        return cuentaService.consultarSaldo(cuentaId);
    }

    @GetMapping("/movimientos")
    public List<MovimientoAnual> listarMovimientos() {
        return cuentaService.obtenerTodosLosMovimientos();
    }

    @GetMapping("/{cuentaId}/movimientos")
    public List<MovimientoAnual> listarMovimientosDeCuenta(@PathVariable Long cuentaId) {
        return cuentaService.obtenerMovimientosDeCuenta(cuentaId);
    }

    /** Retiro: debita de forma atomica y devuelve el saldo resultante. */
    @PostMapping("/{cuentaId}/debito")
    public Double debitar(@PathVariable Long cuentaId, @RequestParam Double monto) {
        return cuentaService.debitar(cuentaId, monto);
    }

    @PostMapping("/{cuentaId}/credito")
    public Double acreditar(@PathVariable Long cuentaId, @RequestParam Double monto) {
        return cuentaService.acreditar(cuentaId, monto);
    }
}
