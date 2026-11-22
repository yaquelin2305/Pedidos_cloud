package cl.duoc.pedidos360.catalog.streaming.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declara los topicos de los que catalog es dueno. Spring Kafka los crea al arrancar si no existen;
 * los brokers tienen {@code auto.create.topics.enable=false}.
 *
 * <p>Justificacion de los valores de {@code application.yml}:
 * <ul>
 *   <li><b>3 particiones:</b> permiten hasta 3 consumidores en paralelo por grupo.</li>
 *   <li><b>RF 3 + {@code min.insync.replicas=2} + {@code acks=all}:</b> cada mensaje confirmado esta en
 *       al menos 2 brokers; se sigue escribiendo con un broker caido. Con dos caidos el productor falla:
 *       se prefiere no aceptar datos a perderlos.</li>
 *   <li><b>Stock con {@code cleanup.policy=compact}:</b> es el estado vigente de cada producto y solo
 *       importa el ultimo valor por key. La compactacion lo conserva indefinidamente, asi un consumidor
 *       nuevo reconstruye el inventario completo leyendo desde el inicio. {@code segment.ms} y
 *       {@code min.cleanable.dirty.ratio} bajos hacen observable la compactacion.</li>
 *   <li><b>DLT con 3 particiones y {@code delete} a 14 dias:</b> el recoverer publica en la misma
 *       particion de origen, asi que necesita las mismas particiones; 14 dias dan margen para
 *       investigar los fallos.</li>
 * </ul>
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic stockTopic(StreamingProperties properties) {
        return toNewTopic(properties.topics().stock());
    }

    @Bean
    NewTopic stockDeadLetterTopic(StreamingProperties properties) {
        return toNewTopic(properties.topics().stockDeadLetter());
    }

    private NewTopic toNewTopic(StreamingProperties.Topic topic) {
        return TopicBuilder.name(topic.name())
                .partitions(topic.partitions())
                .replicas(topic.replicas())
                .configs(topic.configs())
                .build();
    }
}
