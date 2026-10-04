package com.duoc.bffcajero.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // Reenvia el token de la peticion entrante hacia bff-web
        restTemplate.getInterceptors().add(new ReenvioTokenInterceptor());
        return restTemplate;
    }
}
