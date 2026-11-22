package cl.duoc.pedidos360.audit.streaming.config;

import java.util.Map;

import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.audit.streaming.message.OrderEventMessage;
import cl.duoc.pedidos360.audit.streaming.message.StockChangedMessage;

/**
 * Una factory de listeners por tipo de mensaje, porque cada topico tiene su propio DTO. Ambas parten
 * de {@code spring.kafka.consumer.*} (deserializers, grupo, offsets, max.poll.records) y de
 * {@code spring.kafka.listener.*} (ack-mode), y solo cambian {@code spring.json.value.default.type}.
 * El configurer de Spring Boot les aplica tambien el {@code DefaultErrorHandler} de
 * {@link KafkaErrorHandlingConfig}.
 */
@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;
    private final StreamingProperties streamingProperties;
    private final ConcurrentKafkaListenerContainerFactoryConfigurer configurer;

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, OrderEventMessage> orderEventListenerContainerFactory() {
        return listenerFactory(OrderEventMessage.class);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, StockChangedMessage> stockListenerContainerFactory() {
        return listenerFactory(StockChangedMessage.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerFactory(Class<T> messageType) {
        Map<String, Object> consumerProperties = kafkaProperties.buildConsumerProperties();
        consumerProperties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, messageType.getName());
        ConsumerFactory<String, T> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProperties);

        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure((ConcurrentKafkaListenerContainerFactory) factory, (ConsumerFactory) consumerFactory);
        factory.setConcurrency(streamingProperties.concurrency());
        return factory;
    }
}
