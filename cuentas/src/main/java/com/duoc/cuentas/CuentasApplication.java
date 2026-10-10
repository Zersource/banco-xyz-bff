package com.duoc.cuentas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Clase principal de cuentas (EFT): microservicio dueno de los saldos, los
 * movimientos y las transacciones del banco. Se registra en Eureka y toma
 * su configuracion del Config Server.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class CuentasApplication {

    public static void main(String[] args) {
        SpringApplication.run(CuentasApplication.class, args);
    }
}
