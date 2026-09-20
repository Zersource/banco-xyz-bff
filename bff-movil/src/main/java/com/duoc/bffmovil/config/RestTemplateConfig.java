package com.duoc.bffmovil.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate con balanceo de carga (@LoadBalanced): permite llamar a
 * "http://bff-web/..." usando el nombre del servicio registrado en
 * Eureka en vez de un host:puerto fijo.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
