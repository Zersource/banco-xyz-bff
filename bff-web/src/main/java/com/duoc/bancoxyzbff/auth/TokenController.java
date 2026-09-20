package com.duoc.bancoxyzbff.auth;

import com.duoc.bancoxyzbff.auth.dto.TokenRequestDTO;
import com.duoc.bancoxyzbff.auth.dto.TokenResponseDTO;
import com.duoc.bancoxyzbff.exception.CredencialesCanalInvalidasException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Punto de entrada de autenticacion, comun a los 3 canales. No esta
 * protegido por CanalAuthInterceptor (es el unico endpoint publico),
 * porque es justamente donde un canal obtiene su token para acceder
 * despues a su propio BFF.
 */
@RestController
@RequestMapping("/api/auth")
public class TokenController {

    @Autowired
    private JwtService jwtService;

    @Value("${jwt.expiracion-minutos}")
    private long expiracionMinutos;

    @Value("${canal.web.llave}")
    private String llaveWeb;

    @Value("${canal.movil.llave}")
    private String llaveMovil;

    @Value("${canal.cajero.llave}")
    private String llaveCajero;

    private Map<String, String> llavesPorCanal;

    private Map<String, String> llavesPorCanal() {
        if (llavesPorCanal == null) {
            llavesPorCanal = Map.of(
                    "WEB", llaveWeb,
                    "MOVIL", llaveMovil,
                    "CAJERO", llaveCajero
            );
        }
        return llavesPorCanal;
    }

    @PostMapping("/token")
    public TokenResponseDTO obtenerToken(@Valid @RequestBody TokenRequestDTO solicitud) {
        String canal = solicitud.getCanal().toUpperCase();
        String llaveEsperada = llavesPorCanal().get(canal);

        if (llaveEsperada == null || !llaveEsperada.equals(solicitud.getClave())) {
            throw new CredencialesCanalInvalidasException();
        }

        String token = jwtService.generarToken(canal);
        return new TokenResponseDTO(token, canal, expiracionMinutos);
    }
}
