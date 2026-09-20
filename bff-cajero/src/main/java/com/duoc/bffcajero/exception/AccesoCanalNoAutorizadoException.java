package com.duoc.bffcajero.exception;

public class AccesoCanalNoAutorizadoException extends RuntimeException {
    public AccesoCanalNoAutorizadoException(String canalEsperado, String canalDelToken) {
        super("El token pertenece al canal " + canalDelToken + ", pero se requiere " + canalEsperado);
    }
}
