package com.duoc.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Servidor de configuracion centralizado del ecosistema Banco XYZ
 * (Exp3 S6). Los 3 microservicios (bff-web, bff-movil, bff-cajero)
 * consumen sus properties desde aca en vez de tenerlas embebidas,
 * usando el nombre de cada archivo bajo config-repo/ igual al
 * spring.application.name del microservicio que lo consume.
 *
 * Se uso el backend "native" (carpeta classpath:/config-repo), en vez
 * de un repositorio Git/S3 externo como sugiere la guia, para no
 * agregar un repositorio adicional fuera del alcance de la actividad;
 * el comportamiento para los microservicios consumidores es identico.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
