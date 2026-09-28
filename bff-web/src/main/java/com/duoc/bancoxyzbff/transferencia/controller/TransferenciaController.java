package com.duoc.bancoxyzbff.transferencia.controller;

import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaRequestDTO;
import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaResponseDTO;
import com.duoc.bancoxyzbff.transferencia.service.TransferenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
