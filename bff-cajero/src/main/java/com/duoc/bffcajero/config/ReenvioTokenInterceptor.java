package com.duoc.bffcajero.config;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;

/**
 * Reenvia a bff-web el mismo token Bearer con el que llego la peticion
 * entrante, para que bff-web tambien pueda validarlo. Si no hay un token
 * en el contexto de seguridad, la llamada sale sin header Authorization.
 */
public class ReenvioTokenInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwtAutenticacion) {
            request.getHeaders().setBearerAuth(jwtAutenticacion.getToken().getTokenValue());
        }
        return execution.execute(request, body);
    }
}
