package com.duoc.bffmovil.client;

import com.duoc.bffmovil.dto.CuentaDTO;
import com.duoc.bffmovil.dto.TransaccionDTO;
import com.duoc.bffmovil.exception.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Cliente HTTP hacia bff-web, resuelto por nombre de servicio via
 * Eureka (RestTemplate @LoadBalanced, ver RestTemplateConfig). bff-movil
 * ya no tiene su propia copia de los datos: le pide todo a bff-web por
 * su contrato interno (/interno/**).
 *
 * Cada metodo esta protegido con Circuit Breaker (Resilience4j, config
 * "bffWeb" en config-server): si bff-web falla repetidamente o no
 * responde, el circuito se abre y las siguientes llamadas van directo
 * al fallback en vez de seguir esperando contra un servicio caido.
 */
@Component
public class BffWebClient {

    private static final String BASE_URL = "http://bff-web/interno";

    @Autowired
    private RestTemplate restTemplate;

    @CircuitBreaker(name = "bffWeb", fallbackMethod = "obtenerCuentaFallback")
    public CuentaDTO obtenerCuenta(Long cuentaId) {
        return restTemplate.getForObject(BASE_URL + "/cuentas/{id}", CuentaDTO.class, cuentaId);
    }

    @CircuitBreaker(name = "bffWeb", fallbackMethod = "obtenerUltimasTransaccionesFallback")
    public List<TransaccionDTO> obtenerUltimasTransacciones(int cantidad) {
        TransaccionDTO[] resultado = restTemplate.getForObject(
                BASE_URL + "/transacciones/ultimas?cantidad={cantidad}", TransaccionDTO[].class, cantidad);
        return resultado == null ? List.of() : List.of(resultado);
    }

    // Los fallback deben tener la misma firma + un Throwable al final.
    // ignore-exceptions (config-server) solo evita que un 4xx cuente
    // como fallo para abrir el circuito; el aspecto igual invoca este
    // fallback para CUALQUIER excepcion. Por eso hay que distinguir
    // aca: un HttpClientErrorException es una respuesta real y valida
    // de bff-web (ej. 404 cuenta no encontrada), no una caida del
    // servicio, asi que se relanza tal cual para que la capture
    // GlobalExceptionHandler. Cualquier otra causa (timeout, conexion
    // rechazada, circuito abierto) si es una caida real -> 503.
    private CuentaDTO obtenerCuentaFallback(Long cuentaId, Throwable error) {
        if (error instanceof HttpClientErrorException httpError) {
            throw httpError;
        }
        throw new ServicioNoDisponibleException("bff-web", error);
    }

    private List<TransaccionDTO> obtenerUltimasTransaccionesFallback(int cantidad, Throwable error) {
        if (error instanceof HttpClientErrorException httpError) {
            throw httpError;
        }
        throw new ServicioNoDisponibleException("bff-web", error);
    }
}
