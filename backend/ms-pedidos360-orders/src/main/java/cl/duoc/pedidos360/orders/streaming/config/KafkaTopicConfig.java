package cl.duoc.pedidos360.orders.streaming.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declara los topicos de los que orders es dueno. Spring Kafka los crea al arrancar si no existen;
 * los brokers tienen {@code auto.create.topics.enable=false}.
 *
 * <p>Justificacion de los valores de {@code application.yml}:
 * <ul>
 *   <li><b>3 particiones:</b> permiten hasta 3 consumidores en paralelo por grupo.</li>
 *   <li><b>RF 3 + {@code min.insync.replicas=2} + {@code acks=all}:</b> cada mensaje confirmado esta en
 *       al menos 2 brokers; se sigue escribiendo con un broker caido. Con dos caidos el productor falla:
 *       se prefiere no aceptar datos a perderlos.</li>
 *   <li><b>Pedidos con {@code cleanup.policy=delete} y retencion de 7 dias:</b> conserva
 *       el historial para reconstruir las proyecciones sin crecer indefinidamente.</li>
 *   <li><b>DLT con 3 particiones y {@code delete} a 14 dias:</b> el recoverer publica en la misma
 *       particion de origen, asi que necesita las mismas particiones; 14 dias dan margen para
 *       investigar los fallos.</li>
 * </ul>
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic orderEventsTopic(StreamingProperties properties) {
        return toNewTopic(properties.topics().orderEvents());
    }

    @Bean
    NewTopic orderEventsDeadLetterTopic(StreamingProperties properties) {
        return toNewTopic(properties.topics().orderEventsDeadLetter());
    }

    private NewTopic toNewTopic(StreamingProperties.Topic topic) {
        return TopicBuilder.name(topic.name())
                .partitions(topic.partitions())
                .replicas(topic.replicas())
                .configs(topic.configs())
                .build();
    }
}
