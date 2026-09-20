package com.duoc.bffcajero.exception;

public class CuentaNoEncontradaException extends RuntimeException {
    public CuentaNoEncontradaException(Long cuentaId) {
        super("No se encontro la cuenta con id " + cuentaId);
    }
}
