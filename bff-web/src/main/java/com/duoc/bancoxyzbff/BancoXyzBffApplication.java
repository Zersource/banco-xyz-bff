package com.duoc.bancoxyzbff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Clase principal de bff-web.
 *
 * Desde Exp3 S6, este microservicio es el unico que conserva acceso
 * directo a los datos legacy (CSV en memoria) y los expone tanto a su
 * propio canal (/api/web/**) como a bff-movil y bff-cajero a traves de
 * /interno/** (ver InternoController). Se registra en Eureka
 * (@EnableDiscoveryClient) y obtiene su configuracion desde
 * config-server (ver application.yml, spring.config.import).
 */
@SpringBootApplication
@EnableDiscoveryClient
public class BancoXyzBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(BancoXyzBffApplication.class, args);
    }
}
