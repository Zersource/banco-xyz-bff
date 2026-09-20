package com.duoc.bancoxyzbff.exception;

/**
 * Se lanza cuando se consulta una cuenta que no existe en el dataset.
 */
public class CuentaNoEncontradaException extends RuntimeException {

    public CuentaNoEncontradaException(Long cuentaId) {
        super("No se encontro la cuenta con id: " + cuentaId);
    }
}
