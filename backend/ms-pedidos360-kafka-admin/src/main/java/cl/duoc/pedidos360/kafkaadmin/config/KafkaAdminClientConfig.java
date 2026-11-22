package cl.duoc.pedidos360.kafkaadmin.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Crea el unico {@link AdminClient} del servicio con las propiedades que Spring Boot ya arma desde
 * {@code spring.kafka.*} (bootstrap servers y timeouts de {@code spring.kafka.admin.properties}).
 * Se cierra al apagar la aplicacion para liberar las conexiones con los brokers.
 */
@Configuration
public class KafkaAdminClientConfig {

    @Bean(destroyMethod = "close")
    AdminClient adminClient(KafkaAdmin kafkaAdmin) {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }
}
