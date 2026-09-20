package com.duoc.bffmovil.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Valida los JWT emitidos por bff-web (POST /api/auth/token). bff-movil
 * no emite tokens propios, solo verifica la firma con la misma llave
 * HMAC (jwt.secreto, compartida via config-server) y extrae el canal.
 */
@Service
public class JwtService {

    @Value("${jwt.secreto}")
    private String secreto;

    private SecretKey obtenerLlaveFirma() {
        return Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
    }

    public String extraerCanal(String token) throws JwtException, ExpiredJwtException {
        Claims claims = Jwts.parser()
                .verifyWith(obtenerLlaveFirma())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("canal", String.class);
    }
}
