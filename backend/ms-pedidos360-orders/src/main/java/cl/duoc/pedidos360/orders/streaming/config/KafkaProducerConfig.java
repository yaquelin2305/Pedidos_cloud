package cl.duoc.pedidos360.orders.streaming.config;

import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;

import cl.duoc.pedidos360.orders.streaming.message.OrderEventMessage;

/**
 * Productor tipado de {@link OrderEventMessage}. Las propiedades (acks, idempotencia, compresion,
 * timeouts) salen de {@code spring.kafka.producer.*}; aqui solo se construye el {@link JsonSerializer}
 * con el {@link ObjectMapper} de Spring Boot para que las fechas viajen en ISO-8601, igual que en la API.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    ProducerFactory<String, OrderEventMessage> orderProducerFactory(KafkaProperties kafkaProperties,
            ObjectMapper objectMapper) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(),
                new StringSerializer(), new JsonSerializer<>(objectMapper));
    }

    @Bean
    KafkaTemplate<String, OrderEventMessage> orderKafkaTemplate(
            ProducerFactory<String, OrderEventMessage> orderProducerFactory) {
        return new KafkaTemplate<>(orderProducerFactory);
    }
}
