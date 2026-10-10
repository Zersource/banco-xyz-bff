package com.duoc.cuentas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Este microservicio es un resource server OAuth2: valida el access token
 * (JWT) que emite el auth-server. El scope del token decide a que canal
 * se puede entrar: el claim "scope" se convierte en authority SCOPE_xxx.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    @Bean
    public SecurityFilterChain filtroSeguridad(HttpSecurity http,
                                               AutenticacionEntryPoint entryPoint,
                                               AccesoDenegadoHandler accesoDenegadoHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // El retiro lo pide el cajero; el credito y el debito de la saga, bff-web (por ahora)
                        .requestMatchers(HttpMethod.POST, "/cuentas/**")
                            .hasAnyAuthority("SCOPE_cajero", "SCOPE_web")
                        .requestMatchers("/cuentas/**", "/transacciones/**")
                            .hasAnyAuthority("SCOPE_web", "SCOPE_movil", "SCOPE_cajero")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accesoDenegadoHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accesoDenegadoHandler));
        return http.build();
    }
}
