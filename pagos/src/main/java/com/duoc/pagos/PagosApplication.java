package com.duoc.pagos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Clase principal de pagos (EFT): microservicio dueno de las transferencias.
 * Recibe la solicitud, la guarda como PENDIENTE y dispara la saga por Kafka;
 * cuentas ejecuta los pasos sobre los saldos y pagos actualiza el estado.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class PagosApplication {

    public static void main(String[] args) {
        SpringApplication.run(PagosApplication.class, args);
    }
}
