package com.duoc.bancoxyzbff.client;

import com.duoc.bancoxyzbff.config.TokenCanalInterceptor;
import com.duoc.bancoxyzbff.dto.CuentaDTO;
import com.duoc.bancoxyzbff.dto.MovimientoDTO;
import com.duoc.bancoxyzbff.dto.TransaccionDTO;
import com.duoc.bancoxyzbff.exception.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Cliente HTTP hacia el microservicio cuentas, resuelto por nombre de
 * servicio via Eureka. bff-web ya no es dueno de los datos: todo lo pide
 * a cuentas con el token del canal web, protegido con Circuit Breaker
 * (config "cuentas"). Los metodos debitar/acreditar los usa la saga.
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

    @CircuitBreaker(name = "cuentas", fallbackMethod = "listarCuentasFallback")
    public List<CuentaDTO> listarCuentas() {
        return restClient.get().uri("/cuentas").retrieve()
                .body(new ParameterizedTypeReference<List<CuentaDTO>>() { });
    }

    @CircuitBreaker(name = "cuentas", fallbackMethod = "obtenerCuentaFallback")
    public CuentaDTO obtenerCuenta(Long cuentaId) {
        return restClient.get().uri("/cuentas/{id}", cuentaId).retrieve().body(CuentaDTO.class);
    }

    @CircuitBreaker(name = "cuentas", fallbackMethod = "listarMovimientosFallback")
    public List<MovimientoDTO> listarMovimientos() {
        return restClient.get().uri("/cuentas/movimientos").retrieve()
                .body(new ParameterizedTypeReference<List<MovimientoDTO>>() { });
    }

    @CircuitBreaker(name = "cuentas", fallbackMethod = "listarMovimientosDeCuentaFallback")
    public List<MovimientoDTO> listarMovimientosDeCuenta(Long cuentaId) {
        return restClient.get().uri("/cuentas/{id}/movimientos", cuentaId).retrieve()
                .body(new ParameterizedTypeReference<List<MovimientoDTO>>() { });
    }

    @CircuitBreaker(name = "cuentas", fallbackMethod = "listarTransaccionesFallback")
    public List<TransaccionDTO> listarTransacciones() {
        return restClient.get().uri("/transacciones").retrieve()
                .body(new ParameterizedTypeReference<List<TransaccionDTO>>() { });
    }

    @CircuitBreaker(name = "cuentas", fallbackMethod = "consultarSaldoFallback")
    public Double consultarSaldo(Long cuentaId) {
        return restClient.get().uri("/cuentas/{id}/saldo", cuentaId).retrieve().body(Double.class);
    }

    @CircuitBreaker(name = "cuentas", fallbackMethod = "operarSaldoFallback")
    public Double debitar(Long cuentaId, Double monto) {
        return restClient.post().uri("/cuentas/{id}/debito?monto={monto}", cuentaId, monto)
                .retrieve().body(Double.class);
    }

    @CircuitBreaker(name = "cuentas", fallbackMethod = "operarSaldoFallback")
    public Double acreditar(Long cuentaId, Double monto) {
        return restClient.post().uri("/cuentas/{id}/credito?monto={monto}", cuentaId, monto)
                .retrieve().body(Double.class);
    }

    // Los fallback deben tener la misma firma + un Throwable al final.
    // ignore-exceptions (application.properties) solo evita que un 4xx cuente
    // como fallo para abrir el circuito; el aspecto igual invoca el fallback
    // para CUALQUIER excepcion. Un HttpClientErrorException es una respuesta
    // valida de cuentas (404, 400), no una caida: se relanza. Cualquier otra
    // causa (timeout, conexion rechazada, circuito abierto) -> 503.
    private List<CuentaDTO> listarCuentasFallback(Throwable error) {
        throw traducir(error);
    }

    private CuentaDTO obtenerCuentaFallback(Long cuentaId, Throwable error) {
        throw traducir(error);
    }

    private List<MovimientoDTO> listarMovimientosFallback(Throwable error) {
        throw traducir(error);
    }

    private List<MovimientoDTO> listarMovimientosDeCuentaFallback(Long cuentaId, Throwable error) {
        throw traducir(error);
    }

    private List<TransaccionDTO> listarTransaccionesFallback(Throwable error) {
        throw traducir(error);
    }

    private Double consultarSaldoFallback(Long cuentaId, Throwable error) {
        throw traducir(error);
    }

    private Double operarSaldoFallback(Long cuentaId, Double monto, Throwable error) {
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
