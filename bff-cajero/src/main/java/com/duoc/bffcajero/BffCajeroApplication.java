package com.duoc.bffcajero;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Clase principal de bff-cajero (Exp3 S6). Igual que bff-movil: ya no
 * tiene acceso directo a los datos, los pide a bff-web por HTTP via
 * Eureka, con Circuit Breaker en la llamada.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class BffCajeroApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffCajeroApplication.class, args);
    }
}
