package com.duoc.bancoxyzbff.exception;

/**
 * Se lanza cuando se solicita un token en /api/auth/token con un canal
 * inexistente o una clave que no coincide con la esperada para ese canal.
 */
public class CredencialesCanalInvalidasException extends RuntimeException {

    public CredencialesCanalInvalidasException() {
        super("Canal o clave invalidos");
    }
}
