package com.duoc.bancoxyzbff.exception;

/**
 * Se lanza cuando se intenta retirar un monto mayor al saldo disponible.
 */
public class SaldoInsuficienteException extends RuntimeException {

    public SaldoInsuficienteException(Long cuentaId) {
        super("Saldo insuficiente para realizar el retiro en la cuenta: " + cuentaId);
    }
}
