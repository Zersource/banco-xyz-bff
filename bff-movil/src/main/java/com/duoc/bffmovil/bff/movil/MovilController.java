package com.duoc.bffmovil.bff.movil;

import com.duoc.bffmovil.bff.movil.dto.CuentaMovilDTO;
import com.duoc.bffmovil.service.CuentaMovilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BFF para el canal Movil (Exp3 S6): entrega la informacion esencial de
 * la cuenta, con payload reducido. Ya no lee los datos directamente,
 * los pide a bff-web via CuentaMovilService -> BffWebClient.
 */
@RestController
@RequestMapping("/api/movil")
public class MovilController {

    @Autowired
    private CuentaMovilService cuentaMovilService;

    @GetMapping("/cuentas/{cuentaId}")
    public CuentaMovilDTO obtenerCuenta(@PathVariable Long cuentaId) {
        return cuentaMovilService.obtenerCuenta(cuentaId);
    }
}
