package com.duoc.bancoxyzbff.client;

import com.duoc.bancoxyzbff.config.TokenCanalInterceptor;
import com.duoc.bancoxyzbff.dto.ClienteDTO;
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
 * Cliente HTTP hacia el microservicio clientes (via Eureka). El nombre y la
 * edad del titular son datos accesorios de la vista web: si clientes esta
 * caido, el fallback devuelve vacio y la cuenta sale sin esos dos campos,
 * en vez de fallar toda la consulta.
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

    @CircuitBreaker(name = "clientes", fallbackMethod = "listarClientesFallback")
    public List<ClienteDTO> listarClientes() {
        return restClient.get().uri("/clientes").retrieve()
                .body(new ParameterizedTypeReference<List<ClienteDTO>>() { });
    }

    @CircuitBreaker(name = "clientes", fallbackMethod = "obtenerClienteFallback")
    public ClienteDTO obtenerCliente(Long cuentaId) {
        return restClient.get().uri("/clientes/{cuentaId}", cuentaId).retrieve().body(ClienteDTO.class);
    }

    private List<ClienteDTO> listarClientesFallback(Throwable error) {
        if (error instanceof HttpClientErrorException httpError) {
            throw httpError;
        }
        log.warn("clientes no disponible, las cuentas se responden sin nombre ni edad: {}", error.toString());
        return List.of();
    }

    private ClienteDTO obtenerClienteFallback(Long cuentaId, Throwable error) {
        if (error instanceof HttpClientErrorException httpError) {
            throw httpError;
        }
        log.warn("clientes no disponible, la cuenta {} se responde sin nombre ni edad: {}", cuentaId, error.toString());
        return null;
    }
}
