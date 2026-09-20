package com.duoc.bffcajero.bff.cajero;

import com.duoc.bffcajero.bff.cajero.dto.RetiroRequestDTO;
import com.duoc.bffcajero.bff.cajero.dto.RetiroResponseDTO;
import com.duoc.bffcajero.bff.cajero.dto.SaldoDTO;
import com.duoc.bffcajero.service.CuentaCajeroService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BFF para el canal Cajero Automatico (Exp3 S6): solo expone las dos
 * operaciones criticas (consultar saldo y retirar). Ya no accede a los
 * datos directamente, se los pide a bff-web via CuentaCajeroService ->
 * BffWebClient (Circuit Breaker incluido).
 */
@RestController
@RequestMapping("/api/cajero")
public class CajeroController {

    @Autowired
    private CuentaCajeroService cuentaCajeroService;

    @GetMapping("/cuentas/{cuentaId}/saldo")
    public SaldoDTO consultarSaldo(@PathVariable Long cuentaId) {
        return cuentaCajeroService.consultarSaldo(cuentaId);
    }

    @PostMapping("/cuentas/{cuentaId}/retiro")
    public RetiroResponseDTO retirar(@PathVariable Long cuentaId, @Valid @RequestBody RetiroRequestDTO solicitud) {
        return cuentaCajeroService.realizarRetiro(cuentaId, solicitud.getMonto());
    }
}
