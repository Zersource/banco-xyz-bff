package com.duoc.bancoxyzbff.transferencia.controller;

import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaRequestDTO;
import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaResponseDTO;
import com.duoc.bancoxyzbff.transferencia.service.TransferenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Punto de entrada de transferencias del canal web. Reenvia la solicitud a
 * pagos y responde 202: el resto del flujo (debito, credito, compensacion)
 * ocurre de forma asincrona entre pagos y cuentas por Kafka. Los errores
 * de validacion (400) los devuelve pagos y se reenvian tal cual.
 */
@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {

    @Autowired
    private TransferenciaService transferenciaService;

    @PostMapping
    public ResponseEntity<TransferenciaResponseDTO> iniciar(@RequestBody TransferenciaRequestDTO request) {
        TransferenciaResponseDTO respuesta = transferenciaService.iniciarTransferencia(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(respuesta);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferenciaResponseDTO> consultarEstado(@PathVariable Long id) {
        return ResponseEntity.ok(transferenciaService.consultarEstado(id));
    }
}
