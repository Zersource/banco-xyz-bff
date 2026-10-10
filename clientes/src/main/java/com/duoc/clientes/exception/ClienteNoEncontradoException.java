package com.duoc.clientes.exception;

/**
 * Se lanza cuando no hay un cliente asociado a la cuenta consultada.
 */
public class ClienteNoEncontradoException extends RuntimeException {

    public ClienteNoEncontradoException(Long cuentaId) {
        super("No se encontro el cliente de la cuenta con id: " + cuentaId);
    }
}
