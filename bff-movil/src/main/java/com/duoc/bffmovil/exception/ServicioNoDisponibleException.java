package com.duoc.bffmovil.exception;

/**
 * Se lanza desde el fallback de BffWebClient cuando el Circuit Breaker
 * abre el circuito (o la llamada a bff-web falla) en vez de dejar que
 * la excepcion original de RestTemplate se propague sin control.
 */
public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String servicio, Throwable causa) {
        super("El servicio " + servicio + " no esta disponible en este momento", causa);
    }
}
