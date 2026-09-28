package com.duoc.bancoxyzbff.transferencia.controller;

import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaRequestDTO;
import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaResponseDTO;
import com.duoc.bancoxyzbff.transferencia.service.TransferenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Punto de entrada sincrono de la saga: recibe la solicitud de
 * transferencia, la persiste como PENDIENTE y dispara el primer evento.
 * El resto del flujo (debito, credito, compensacion) ocurre de forma
 * asincrona via los listeners JMS.
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
        TransferenciaResponseDTO respuesta = transferenciaService.consultarEstado(id);
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Handler local (no en el GlobalExceptionHandler compartido por los 3
     * BFF, a pedido explicito): mapea las validaciones de entrada de la
     * saga (monto <= 0, cuentas nulas, origen == destino) a 400.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> manejarSolicitudInvalida(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
    }
}
