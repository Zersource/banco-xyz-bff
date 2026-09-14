package com.duoc.bancoxyzbff.config;

import com.duoc.bancoxyzbff.auth.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra un CanalAuthInterceptor distinto para cada BFF, cada uno
 * aplicado solo a las rutas de su propio canal (/api/web/**, /api/movil/**,
 * /api/cajero/**), validando el JWT contra el canal que le corresponde.
 * /api/auth/** queda fuera de todos los interceptores: es el endpoint
 * publico donde se obtiene el token.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtService jwtService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CanalAuthInterceptor("WEB", jwtService))
                .addPathPatterns("/api/web/**");

        registry.addInterceptor(new CanalAuthInterceptor("MOVIL", jwtService))
                .addPathPatterns("/api/movil/**");

        registry.addInterceptor(new CanalAuthInterceptor("CAJERO", jwtService))
                .addPathPatterns("/api/cajero/**");
    }
}
