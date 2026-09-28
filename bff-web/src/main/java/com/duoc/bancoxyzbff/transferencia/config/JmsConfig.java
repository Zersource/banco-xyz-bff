package com.duoc.bancoxyzbff.transferencia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * Configuracion del broker JMS embebido (ActiveMQ Artemis).
 *
 * Se usa MappingJackson2MessageConverter para que los mensajes viajen
 * como JSON de texto (mas facil de inspeccionar en capturas de evidencia
 * y en la consola web de Artemis) en vez de ObjectMessage serializado
 * en binario.
 *
 * Los nombres de las colas quedan centralizados aca como constantes para
 * que productor y listeners no dupliquen strings.
 */
@Configuration
public class JmsConfig {

    public static final String COLA_TRANSFERENCIA_INICIADA = "queue.transferencia.iniciada";
    public static final String COLA_DEBITO_REALIZADO = "queue.debito.realizado";
    public static final String COLA_TRANSFERENCIA_FALLIDA = "queue.transferencia.fallida";
    public static final String COLA_TRANSFERENCIA_COMPLETADA = "queue.transferencia.completada";
    public static final String COLA_TRANSFERENCIA_COMPENSACION = "queue.transferencia.compensacion";
    public static final String COLA_TRANSFERENCIA_REVERTIDA = "queue.transferencia.revertida";

    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        MappingJackson2MessageConverter conversor = new MappingJackson2MessageConverter();
        conversor.setTargetType(MessageType.TEXT);
        conversor.setTypeIdPropertyName("_tipo");
        return conversor;
    }
}
