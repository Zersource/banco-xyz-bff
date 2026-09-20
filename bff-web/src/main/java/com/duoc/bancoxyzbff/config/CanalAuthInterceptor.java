package com.duoc.bancoxyzbff.config;

import com.duoc.bancoxyzbff.auth.JwtService;
import com.duoc.bancoxyzbff.exception.AccesoCanalNoAutorizadoException;
import com.duoc.bancoxyzbff.exception.TokenInvalidoException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Valida el token JWT (header "Authorization: Bearer ...") para el canal
 * que corresponde a este BFF. Separa dos problemas distintos:
 *
 * - Autenticacion: el token no vino, esta corrupto, mal firmado o vencido
 *   -> TokenInvalidoException (401 Unauthorized). No se pudo confirmar
 *   quien es el que llama.
 * - Autorizacion: el token es valido y pertenece a un canal real, pero
 *   ese canal no es el que corresponde a esta ruta (ej. un token de MOVIL
 *   intentando entrar a /api/web/**) -> AccesoCanalNoAutorizadoException
 *   (403 Forbidden). Sabemos quien es, pero no tiene permiso aca.
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
