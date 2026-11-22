package cl.duoc.pedidos360.kafkaadmin.config;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del administrador, leida desde {@code pedidos360.kafka-admin}.
 *
 * @param protectedTopics topicos del sistema que no se pueden eliminar
 */
@ConfigurationProperties("pedidos360.kafka-admin")
public record KafkaAdminProperties(Set<String> protectedTopics) {

    /** Los topicos internos de Kafka (por ejemplo {@code __consumer_offsets}) empiezan con este prefijo. */
    private static final String INTERNAL_PREFIX = "_";

    public KafkaAdminProperties {
        protectedTopics = protectedTopics == null ? Set.of() : Set.copyOf(protectedTopics);
    }

    public boolean isProtected(String topic) {
        return topic.startsWith(INTERNAL_PREFIX) || protectedTopics.contains(topic);
    }
}
