package com.duoc.bancoxyzbff.exception;

/**
 * Se lanza cuando el token es valido (autenticacion correcta) pero
 * pertenece a un canal distinto al que exige la ruta solicitada. Es un
 * problema de AUTORIZACION (sabemos quien es, pero no tiene permiso
 * sobre este BFF), por eso se traduce a 403, distinto del 401 de
 * TokenInvalidoException.
 */
public class AccesoCanalNoAutorizadoException extends RuntimeException {

    public AccesoCanalNoAutorizadoException(String canalEsperado, String canalDelToken) {
        super("El token pertenece al canal " + canalDelToken
                + " y no tiene permiso para acceder al canal " + canalEsperado);
    }
}
