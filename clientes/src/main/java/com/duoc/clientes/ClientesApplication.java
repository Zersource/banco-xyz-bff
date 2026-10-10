package com.duoc.clientes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Clase principal de clientes (EFT): microservicio con los datos del
 * titular de cada cuenta (cuenta_id, nombre, edad). Se registra en Eureka
 * y toma su configuracion del Config Server.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ClientesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientesApplication.class, args);
    }
}
