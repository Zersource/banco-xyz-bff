package com.duoc.cuentas.controller;

import com.duoc.cuentas.model.Transaccion;
import com.duoc.cuentas.service.CuentaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Actividad general del banco (transacciones.csv no tiene cuenta_id, por
 * eso no cuelga de /cuentas/{id}).
 */
@RestController
@RequestMapping("/transacciones")
public class TransaccionController {

    @Autowired
    private CuentaService cuentaService;

    @GetMapping
    public List<Transaccion> listar() {
        return cuentaService.obtenerTransaccionesGenerales();
    }

    @GetMapping("/ultimas")
    public List<Transaccion> ultimas(@RequestParam(defaultValue = "5") int cantidad) {
        return cuentaService.obtenerUltimasTransacciones(cantidad);
    }
}
