package com.duoc.pagos.saga.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics de la saga que usa pagos. Se declaran de forma explicita (3
 * particiones, replicacion 1) para que, al escalar pagos a 2 instancias, el
 * consumo se reparta entre ambas; sin esto el broker los crearia solos con 1
 * particion. Cada servicio declara los topics que publica o consume.
 */
@Configuration
public class KafkaTopicsConfig {

    public static final String TRANSFERENCIA_INICIADA = "transferencia.iniciada";
    public static final String DEBITO_FALLIDO = "transferencia.debito-fallido";
    public static final String COMPLETADA = "transferencia.completada";
    public static final String REVERTIDA = "transferencia.revertida";

    public static final String GRUPO = "pagos-estado";

    @Bean
    public NewTopic topicIniciada() {
        return crear(TRANSFERENCIA_INICIADA);
    }

    @Bean
    public NewTopic topicDebitoFallido() {
        return crear(DEBITO_FALLIDO);
    }

    @Bean
    public NewTopic topicCompletada() {
        return crear(COMPLETADA);
    }

    @Bean
    public NewTopic topicRevertida() {
        return crear(REVERTIDA);
    }

    private NewTopic crear(String nombre) {
        return TopicBuilder.name(nombre).partitions(3).replicas(1).build();
    }
}
