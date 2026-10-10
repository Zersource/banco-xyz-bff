package com.duoc.clientes.service.impl;

import com.duoc.clientes.exception.ClienteNoEncontradoException;
import com.duoc.clientes.model.Cliente;
import com.duoc.clientes.repository.ClienteRepository;
import com.duoc.clientes.service.ClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClienteServiceImpl implements ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Override
    public List<Cliente> obtenerTodosLosClientes() {
        return clienteRepository.buscarTodos();
    }

    @Override
    public Cliente obtenerClientePorCuentaId(Long cuentaId) {
        return clienteRepository.buscarPorCuentaId(cuentaId)
                .orElseThrow(() -> new ClienteNoEncontradoException(cuentaId));
    }
}
