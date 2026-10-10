package com.duoc.bancoxyzbff.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Configuracion de las llamadas salientes: un RestClient.Builder con
 * balanceo por Eureka (http://cuentas) y el manager que obtiene, con el
 * flujo client_credentials, el token del canal web (se cachea y se
 * renueva solo cuando vence).
 */
@Configuration
public class RestClientConfig {

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    @Bean
    @LoadBalanced
    public RestClient.Builder restClientBuilder() {
        // Una conexion nueva por llamada ("Connection: close"): HttpURLConnection no reintenta
        // un POST sobre una conexion reutilizada que el servidor ya cerro (por ejemplo tras
        // reiniciar cuentas o pagos), y esa primera llamada fallaba con un 503.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setRequestProperty("Connection", "close");
            }
        };
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager() {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }
}
