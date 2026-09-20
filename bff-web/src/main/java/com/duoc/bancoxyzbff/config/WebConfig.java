package com.duoc.bancoxyzbff.config;

import com.duoc.bancoxyzbff.auth.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra el CanalAuthInterceptor del canal Web, validando el JWT
 * contra /api/web/**. Desde Exp3 S6, movil y cajero son microservicios
 * separados y registran su propio WebConfig con su propio interceptor.
 * /api/auth/** y /interno/** quedan fuera del interceptor: el primero
 * es el endpoint publico donde se obtiene el token, y el segundo es
 * trafico interno entre microservicios (ver InternoController).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtService jwtService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CanalAuthInterceptor("WEB", jwtService))
                .addPathPatterns("/api/web/**");
    }
}
