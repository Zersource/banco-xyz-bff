package com.duoc.clientes.service;

import com.duoc.clientes.model.Cliente;

import java.util.List;

public interface ClienteService {

    List<Cliente> obtenerTodosLosClientes();

    /**
     * @throws com.duoc.clientes.exception.ClienteNoEncontradoException si no hay cliente para la cuenta.
     */
    Cliente obtenerClientePorCuentaId(Long cuentaId);
}
