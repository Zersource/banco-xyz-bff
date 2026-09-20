package com.duoc.bffcajero.config;

import com.duoc.bffcajero.auth.JwtService;
import com.duoc.bffcajero.exception.AccesoCanalNoAutorizadoException;
import com.duoc.bffcajero.exception.TokenInvalidoException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Igual al CanalAuthInterceptor de bff-web y bff-movil: valida el JWT
 * del header Authorization contra el canal esperado (CAJERO).
 */
public class CanalAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_AUTORIZACION = "Authorization";
    private static final String PREFIJO_BEARER = "Bearer ";

    private final String canalEsperado;
    private final JwtService jwtService;

    public CanalAuthInterceptor(String canalEsperado, JwtService jwtService) {
        this.canalEsperado = canalEsperado;
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HEADER_AUTORIZACION);

        if (header == null || !header.startsWith(PREFIJO_BEARER)) {
            throw new TokenInvalidoException("Falta el header Authorization con el token Bearer");
        }

        String token = header.substring(PREFIJO_BEARER.length());
        String canalDelToken;

        try {
            canalDelToken = jwtService.extraerCanal(token);
        } catch (JwtException e) {
            throw new TokenInvalidoException("Token invalido o expirado");
        }

        if (!canalEsperado.equals(canalDelToken)) {
            throw new AccesoCanalNoAutorizadoException(canalEsperado, canalDelToken);
        }

        return true;
    }
}
