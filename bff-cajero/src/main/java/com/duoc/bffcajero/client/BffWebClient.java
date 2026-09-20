package com.duoc.bffcajero.client;

import com.duoc.bffcajero.exception.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Cliente HTTP hacia bff-web, resuelto por nombre de servicio via Eureka.
 * bff-cajero ya no tiene su propia copia de los datos: consulta saldo y
 * procesa retiros contra el contrato interno de bff-web (/interno/**),
 * cada llamada protegida con Circuit Breaker (config "bffWeb").
 */
@Component
public class BffWebClient {

    private static final String BASE_URL = "http://bff-web/interno";

    @Autowired
    private RestTemplate restTemplate;

    @CircuitBreaker(name = "bffWeb", fallbackMethod = "consultarSaldoFallback")
    public Double consultarSaldo(Long cuentaId) {
        return restTemplate.getForObject(BASE_URL + "/cuentas/{id}/saldo", Double.class, cuentaId);
    }

    @CircuitBreaker(name = "bffWeb", fallbackMethod = "realizarRetiroFallback")
    public Double realizarRetiro(Long cuentaId, Double monto) {
        String url = BASE_URL + "/cuentas/{id}/retiro?monto={monto}";
        return restTemplate.postForObject(url, null, Double.class, cuentaId, monto);
    }

    // ignore-exceptions (config-server) solo evita que un 4xx cuente
    // como fallo para abrir el circuito; el aspecto igual invoca este
    // fallback para CUALQUIER excepcion. Por eso hay que distinguir
    // aca: un HttpClientErrorException es una respuesta real y valida
    // de bff-web (ej. 404 cuenta no encontrada, 400 saldo insuficiente),
    // no una caida del servicio, asi que se relanza tal cual para que
    // la capture GlobalExceptionHandler. Cualquier otra causa (timeout,
    // conexion rechazada, circuito abierto) si es una caida real -> 503.
    private Double consultarSaldoFallback(Long cuentaId, Throwable error) {
        if (error instanceof HttpClientErrorException httpError) {
            throw httpError;
        }
        throw new ServicioNoDisponibleException("bff-web", error);
    }

    private Double realizarRetiroFallback(Long cuentaId, Double monto, Throwable error) {
        if (error instanceof HttpClientErrorException httpError) {
            throw httpError;
        }
        throw new ServicioNoDisponibleException("bff-web", error);
    }
}
