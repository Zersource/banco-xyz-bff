package com.duoc.bancoxyzbff.exception;

/**
 * Se lanza cuando el token JWT no viene, esta corrupto, mal firmado o
 * vencido. Es un problema de AUTENTICACION (no se pudo confirmar la
 * identidad del canal que llama), por eso se traduce a 401.
 */
public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException(String mensaje) {
        super(mensaje);
    }
}
