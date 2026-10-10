package com.duoc.bffcajero.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Agrega a cada llamada saliente el token client_credentials de este canal
 * (registro "canal" en application.properties). Ya no se reenvia el token
 * del usuario: cada BFF se identifica con su propio cliente OAuth2.
 */
@Component
public class TokenCanalInterceptor implements ClientHttpRequestInterceptor {

    private static final String REGISTRO_CANAL = "canal";

    @Autowired
    private OAuth2AuthorizedClientManager authorizedClientManager;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        OAuth2AuthorizeRequest solicitud = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRO_CANAL)
                .principal("bff-cajero")
                .build();
        OAuth2AuthorizedClient cliente = authorizedClientManager.authorize(solicitud);
        if (cliente != null) {
            request.getHeaders().setBearerAuth(cliente.getAccessToken().getTokenValue());
        }
        return execution.execute(request, body);
    }
}
