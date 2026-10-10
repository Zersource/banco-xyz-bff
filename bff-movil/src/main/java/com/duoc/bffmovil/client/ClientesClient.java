package com.duoc.bffmovil.client;

import com.duoc.bffmovil.config.TokenCanalInterceptor;
import com.duoc.bffmovil.dto.ClienteDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia el microservicio clientes (via Eureka). El nombre del
 * titular es un dato accesorio del canal movil: si clientes esta caido el
 * fallback devuelve null y la respuesta sale sin nombre, en vez de fallar
 * toda la consulta de la cuenta.
 */
@Component
public class ClientesClient {

    private static final Logger log = LoggerFactory.getLogger(ClientesClient.class);

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private TokenCanalInterceptor tokenCanalInterceptor;

    private RestClient restClient;

    @PostConstruct
    void inicializar() {
        restClient = restClientBuilder
                .baseUrl("http://clientes")
                .requestInterceptor(tokenCanalInterceptor)
                .build();
    }

    @CircuitBreaker(name = "clientes", fallbackMethod = "obtenerClienteFallback")
    public ClienteDTO obtenerCliente(Long cuentaId) {
        return restClient.get()
                .uri("/clientes/{cuentaId}", cuentaId)
                .retrieve()
                .body(ClienteDTO.class);
    }

    private ClienteDTO obtenerClienteFallback(Long cuentaId, Throwable error) {
        if (error instanceof HttpClientErrorException httpError) {
            throw httpError;
        }
        log.warn("clientes no disponible, la cuenta {} se responde sin nombre: {}", cuentaId, error.toString());
        return null;
    }
}
