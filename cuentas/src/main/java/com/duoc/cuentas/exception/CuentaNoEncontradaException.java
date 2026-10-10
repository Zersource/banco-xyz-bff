package com.duoc.cuentas.exception;

/**
 * Se lanza cuando se consulta o se opera sobre una cuenta que no existe.
 */
public class CuentaNoEncontradaException extends RuntimeException {

    public CuentaNoEncontradaException(Long cuentaId) {
        super("No se encontro la cuenta con id: " + cuentaId);
    }
}
