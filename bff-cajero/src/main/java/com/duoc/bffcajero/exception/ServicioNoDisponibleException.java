package com.duoc.bffcajero.exception;

/**
 * Se lanza desde el fallback de BffWebClient cuando el Circuit Breaker
 * abre el circuito (o la llamada a bff-web falla).
 */
public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String servicio, Throwable causa) {
        super("El servicio " + servicio + " no esta disponible en este momento", causa);
    }
}
