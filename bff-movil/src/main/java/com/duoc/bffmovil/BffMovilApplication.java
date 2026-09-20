package com.duoc.bffmovil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Clase principal de bff-movil (Exp3 S6).
 *
 * Ya no tiene acceso directo a los datos: obtiene todo desde bff-web
 * por HTTP (ver BffWebClient), resuelto por nombre de servicio via
 * Eureka (@EnableDiscoveryClient), con Circuit Breaker en la llamada.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class BffMovilApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffMovilApplication.class, args);
    }
}
