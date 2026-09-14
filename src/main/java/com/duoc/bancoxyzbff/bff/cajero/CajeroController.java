package com.duoc.bancoxyzbff.bff.cajero;

import com.duoc.bancoxyzbff.bff.cajero.dto.RetiroRequestDTO;
import com.duoc.bancoxyzbff.bff.cajero.dto.RetiroResponseDTO;
import com.duoc.bancoxyzbff.bff.cajero.dto.SaldoDTO;
import com.duoc.bancoxyzbff.service.CuentaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BFF para el canal Cajero Automatico: solo expone las dos operaciones
 * criticas que necesita este canal (consultar saldo y retirar), sin
 * datos personales ni historial, por ser un canal publico y sensible.
 */
@RestController
@RequestMapping("/api/cajero")
public class CajeroController {

    @Autowired
    private CuentaService cuentaService;

    @GetMapping("/cuentas/{cuentaId}/saldo")
    public SaldoDTO consultarSaldo(@PathVariable Long cuentaId) {
        Double saldo = cuentaService.consultarSaldo(cuentaId);
        return new SaldoDTO(cuentaId, saldo);
    }

    @PostMapping("/cuentas/{cuentaId}/retiro")
    public RetiroResponseDTO retirar(@PathVariable Long cuentaId, @Valid @RequestBody RetiroRequestDTO solicitud) {
        Double saldoResultante = cuentaService.realizarRetiro(cuentaId, solicitud.getMonto());
        return new RetiroResponseDTO(cuentaId, solicitud.getMonto(), saldoResultante);
    }
}
