package com.duoc.clientes.saga.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topic de la saga que consume clientes. Se declara de forma explicita (3
 * particiones, replicacion 1) para que el consumo se reparta entre
 * instancias; sin esto el broker lo crearia solo con 1 particion.
 */
@Configuration
public class KafkaTopicsConfig {

    public static final String COMPLETADA = "transferencia.completada";

    public static final String GRUPO = "clientes-notificaciones";

    @Bean
    public NewTopic topicCompletada() {
        return TopicBuilder.name(COMPLETADA).partitions(3).replicas(1).build();
    }
}
