package com.duoc.bancoxyzbff.bff.movil;

import com.duoc.bancoxyzbff.bff.movil.dto.CuentaMovilDTO;
import com.duoc.bancoxyzbff.model.Cuenta;
import com.duoc.bancoxyzbff.service.CuentaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BFF para el canal Movil: entrega solo la informacion esencial de la
 * cuenta, con payload reducido pensado para conexiones moviles.
 */
@RestController
@RequestMapping("/api/movil")
public class MovilController {

    private static final int CANTIDAD_ULTIMOS_MOVIMIENTOS = 5;

    @Autowired
    private CuentaService cuentaService;

    @GetMapping("/cuentas/{cuentaId}")
    public CuentaMovilDTO obtenerCuenta(@PathVariable Long cuentaId) {
        Cuenta cuenta = cuentaService.obtenerCuentaPorId(cuentaId);
        var ultimasTransacciones = cuentaService.obtenerUltimasTransacciones(CANTIDAD_ULTIMOS_MOVIMIENTOS);
        return new CuentaMovilDTO(cuenta, ultimasTransacciones);
    }
}
