package com.duoc.bancoxyzbff.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Emite y valida los JWT usados por el CanalAuthInterceptor.
 *
 * Cada token lleva un claim "canal" (WEB, MOVIL o CAJERO) que indica para
 * que BFF fue emitido. La firma es HMAC-SHA256 con una llave secreta
 * definida en application.properties (jwt.secreto).
 *
 * No se implemento un flujo de login con usuarios/roles reales porque la
 * actividad pide "gestionar autenticacion y autorizacion especifica por
 * canal", no un sistema de identidad de usuarios finales: el token
 * reemplaza la llave estatica de S4 por un mecanismo real (firma +
 * expiracion), manteniendo el mismo alcance conceptual: un canal se
 * autentica y solo tiene autorizacion sobre su propio BFF.
 */
@Service
public class JwtService {

    @Value("${jwt.secreto}")
    private String secreto;

    @Value("${jwt.expiracion-minutos}")
    private long expiracionMinutos;

    private SecretKey obtenerLlaveFirma() {
        return Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Genera un token firmado para el canal indicado, valido por
     * jwt.expiracion-minutos minutos.
     */
    public String generarToken(String canal) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + expiracionMinutos * 60 * 1000);

        return Jwts.builder()
                .subject(canal)
                .claim("canal", canal)
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(obtenerLlaveFirma())
                .compact();
    }

    /**
     * Valida la firma y expiracion del token, y devuelve el canal para el
     * que fue emitido.
     *
     * Lanza JwtException (firma invalida, token corrupto) o
     * ExpiredJwtException (vencido) si el token no es valido; ambos casos
     * se traducen a 401 en el interceptor, porque son problemas de
     * autenticacion (no se pudo confirmar la identidad del canal).
     */
    public String extraerCanal(String token) throws JwtException, ExpiredJwtException {
        Claims claims = Jwts.parser()
                .verifyWith(obtenerLlaveFirma())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("canal", String.class);
    }
}
