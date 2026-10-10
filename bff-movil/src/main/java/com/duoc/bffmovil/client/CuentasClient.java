package com.duoc.bffmovil.client;

import com.duoc.bffmovil.config.TokenCanalInterceptor;
import com.duoc.bffmovil.dto.CuentaDTO;
import com.duoc.bffmovil.dto.TransaccionDTO;
import com.duoc.bffmovil.exception.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Cliente HTTP hacia el microservicio cuentas, resuelto por nombre de
 * servicio via Eureka (RestClient @LoadBalanced, ver RestClientConfig). Cada
 * llamada va con el token del canal movil y esta protegida con Circuit
 * Breaker (config "cuentas"): si cuentas falla repetidamente o no responde,
 * el circuito se abre y las siguientes llamadas van directo al fallback.
 */
@Component
public class CuentasClient {

    private static final Logger log = LoggerFactory.getLogger(CuentasClient.class);

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private TokenCanalInterceptor tokenCanalInterceptor;

    private RestClient restClient;

    @PostConstruct
    void inicializar() {
        restClient = restClientBuilder
                .baseUrl("http://cuentas")
                .requestInterceptor(tokenCanalInterceptor)
                .build();
    }

    @CircuitBreaker(name = "cuentas", fallbackMethod = "obtenerCuentaFallback")
    public CuentaDTO obtenerCuenta(Long cuentaId) {
        return restClient.get()
                .uri("/cuentas/{id}", cuentaId)
                .retrieve()
                .body(CuentaDTO.class);
    }

    @CircuitBreaker(name = "cuentas", fallbackMethod = "obtenerUltimasTransaccionesFallback")
    public List<TransaccionDTO> obtenerUltimasTransacciones(int cantidad) {
        TransaccionDTO[] resultado = restClient.get()
                .uri("/transacciones/ultimas?cantidad={cantidad}", cantidad)
                .retrieve()
                .body(TransaccionDTO[].class);
        return resultado == null ? List.of() : List.of(resultado);
    }

    // Los fallback deben tener la misma firma + un Throwable al final.
    // ignore-exceptions (application.properties) solo evita que un 4xx cuente
    // como fallo para abrir el circuito; el aspecto igual invoca el fallback
    // para CUALQUIER excepcion. Un HttpClientErrorException es una respuesta
    // valida de cuentas (ej. 404 cuenta no encontrada), no una caida: se
    // relanza para que lo capture GlobalExceptionHandler. Cualquier otra
    // causa (timeout, conexion rechazada, circuito abierto) es una caida
    // real -> 503.
    private CuentaDTO obtenerCuentaFallback(Long cuentaId, Throwable error) {
        throw traducir(error);
    }

    private List<TransaccionDTO> obtenerUltimasTransaccionesFallback(int cantidad, Throwable error) {
        throw traducir(error);
    }

    private RuntimeException traducir(Throwable error) {
        if (error instanceof HttpClientErrorException httpError) {
            return httpError;
        }
        log.warn("cuentas no disponible, se responde 503: {}", error.toString());
        return new ServicioNoDisponibleException("cuentas", error);
    }
}
