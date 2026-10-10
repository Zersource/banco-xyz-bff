package com.duoc.bancoxyzbff.bff.web;

import com.duoc.bancoxyzbff.bff.web.dto.CuentaWebDTO;
import com.duoc.bancoxyzbff.dto.TransaccionDTO;
import com.duoc.bancoxyzbff.service.CuentaWebService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * BFF para el canal Web: entrega la informacion completa de cada cuenta,
 * pensado para una interfaz de escritorio con espacio para mostrar
 * todo el detalle disponible. Los datos los pide a los microservicios
 * cuentas y clientes (via CuentaWebService).
 */
@RestController
@RequestMapping("/api/web")
public class WebController {

    @Autowired
    private CuentaWebService cuentaWebService;

    @GetMapping("/cuentas")
    public List<CuentaWebDTO> listarCuentas() {
        return cuentaWebService.obtenerCuentas();
    }

    @GetMapping("/cuentas/{cuentaId}")
    public CuentaWebDTO obtenerCuenta(@PathVariable Long cuentaId) {
        return cuentaWebService.obtenerCuenta(cuentaId);
    }

    @GetMapping("/transacciones")
    public List<TransaccionDTO> listarTransaccionesGenerales() {
        return cuentaWebService.obtenerTransaccionesGenerales();
    }
}
