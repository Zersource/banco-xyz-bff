package com.duoc.bancoxyzbff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal del proyecto Banco XYZ BFF.
 * Expone 3 backends independientes (Web, Movil, Cajero) que acceden
 * a los mismos datos legacy pero entregan informacion personalizada
 * segun el canal que consulta.
 */
@SpringBootApplication
public class BancoXyzBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(BancoXyzBffApplication.class, args);
    }
}
