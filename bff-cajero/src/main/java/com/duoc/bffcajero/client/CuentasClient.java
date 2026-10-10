package com.duoc.bffcajero.client;

import com.duoc.bffcajero.config.TokenCanalInterceptor;
import com.duoc.bffcajero.exception.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia el microservicio cuentas, resuelto por nombre de
 * servicio via Eureka. Consulta saldo y procesa retiros (el retiro es un
* debito atomico en cuentas). Cada llamada va con el token del canal y
 * protegida con Circuit Breaker (config "cuentas").
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

    @CircuitBreaker(name = "cuentas", fallbackMethod = "consultarSaldoFallback")
    public Double consultarSaldo(Long cuentaId) {
        return restClient.get()
                .uri("/cuentas/{id}/saldo", cuentaId)
                .retrieve()
                .body(Double.class);
    }

    @CircuitBreaker(name = "cuentas", fallbackMethod = "retirarFallback")
    public Double retirar(Long cuentaId, Double monto) {
        return restClient.post()
                .uri("/cuentas/{id}/retiro?monto={monto}", cuentaId, monto)
                .retrieve()
                .body(Double.class);
    }

    // ignore-exceptions (application.properties) solo evita que un 4xx cuente
    // como fallo para abrir el circuito; el aspecto igual invoca este
    // fallback para CUALQUIER excepcion. Un HttpClientErrorException es una
    // respuesta valida de cuentas (404 cuenta no encontrada, 400 saldo
    // insuficiente), no una caida: se relanza para que lo capture el
    // GlobalExceptionHandler. Cualquier otra causa (timeout, conexion
    // rechazada, circuito abierto) es una caida real -> 503.
    private Double consultarSaldoFallback(Long cuentaId, Throwable error) {
        throw traducir(error);
    }

    private Double retirarFallback(Long cuentaId, Double monto, Throwable error) {
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
