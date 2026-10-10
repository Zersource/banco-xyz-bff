package com.duoc.bancoxyzbff.client;

import com.duoc.bancoxyzbff.config.TokenCanalInterceptor;
import com.duoc.bancoxyzbff.exception.ServicioNoDisponibleException;
import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaRequestDTO;
import com.duoc.bancoxyzbff.transferencia.dto.TransferenciaResponseDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia el microservicio pagos, resuelto por nombre de servicio
 * via Eureka. Cada llamada va con el token del canal web y protegida con
 * Circuit Breaker (config "pagos"). Si pagos no responde, el fallback
 * devuelve un 503 controlado; un 4xx de pagos (400 validacion, 404 no
 * encontrada) es una respuesta valida y se reenvia tal cual.
 */
@Component
public class PagosClient {

    private static final Logger log = LoggerFactory.getLogger(PagosClient.class);

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private TokenCanalInterceptor tokenCanalInterceptor;

    private RestClient restClient;

    @PostConstruct
    void inicializar() {
        restClient = restClientBuilder
                .baseUrl("http://pagos")
                .requestInterceptor(tokenCanalInterceptor)
                .build();
    }

    @CircuitBreaker(name = "pagos", fallbackMethod = "iniciarTransferenciaFallback")
    public TransferenciaResponseDTO iniciarTransferencia(TransferenciaRequestDTO solicitud) {
        return restClient.post()
                .uri("/transferencias")
                .body(solicitud)
                .retrieve()
                .body(TransferenciaResponseDTO.class);
    }

    @CircuitBreaker(name = "pagos", fallbackMethod = "consultarEstadoFallback")
    public TransferenciaResponseDTO consultarEstado(String transaccionId) {
        return restClient.get()
                .uri("/transferencias/{id}", transaccionId)
                .retrieve()
                .body(TransferenciaResponseDTO.class);
    }

    private TransferenciaResponseDTO iniciarTransferenciaFallback(TransferenciaRequestDTO solicitud, Throwable error) {
        throw traducir(error);
    }

    private TransferenciaResponseDTO consultarEstadoFallback(String transaccionId, Throwable error) {
        throw traducir(error);
    }

    private RuntimeException traducir(Throwable error) {
        if (error instanceof HttpClientErrorException httpError) {
            return httpError;
        }
        log.warn("pagos no disponible, se responde 503: {}", error.toString());
        return new ServicioNoDisponibleException("pagos", error);
    }
}
