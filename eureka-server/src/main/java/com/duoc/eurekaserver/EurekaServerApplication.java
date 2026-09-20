package com.duoc.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Servidor de Service Discovery (Exp3 S6). No consume su propia
 * configuracion desde config-server (se mantiene autonomo, como
 * recomienda Spring Cloud: si Eureka dependiera de Config Server,
 * y Config Server fuera descubierto via Eureka, habria una
 * dependencia circular en el arranque).
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
