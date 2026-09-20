package com.duoc.bffcajero.config;

import com.duoc.bffcajero.auth.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra el CanalAuthInterceptor del canal Cajero, validando el JWT
 * (emitido por bff-web) contra /api/cajero/**.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtService jwtService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CanalAuthInterceptor("CAJERO", jwtService))
                .addPathPatterns("/api/cajero/**");
    }
}
