package com.duoc.clientes.controller;

import com.duoc.clientes.model.Cliente;
import com.duoc.clientes.service.ClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de clientes, consumida por los BFF a traves de Eureka.
 */
@RestController
@RequestMapping("/clientes")
public class ClienteController {

    @Autowired
    private ClienteService clienteService;

    @GetMapping
    public List<Cliente> listar() {
        return clienteService.obtenerTodosLosClientes();
    }

    @GetMapping("/{cuentaId}")
    public Cliente obtenerPorCuentaId(@PathVariable Long cuentaId) {
        return clienteService.obtenerClientePorCuentaId(cuentaId);
    }
}
