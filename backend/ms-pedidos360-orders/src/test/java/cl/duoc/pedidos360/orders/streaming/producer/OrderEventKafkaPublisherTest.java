package cl.duoc.pedidos360.orders.streaming.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import cl.duoc.pedidos360.orders.entity.OrderStatus;
import cl.duoc.pedidos360.orders.event.OrderEvent;
import cl.duoc.pedidos360.orders.streaming.config.StreamingProperties;
import cl.duoc.pedidos360.orders.streaming.message.OrderEventMessage;

@ExtendWith(MockitoExtension.class)
class OrderEventKafkaPublisherTest {

    private static final String TOPIC = "pedidos360.orders.stock";

    @Mock
    private KafkaTemplate<String, OrderEventMessage> kafkaTemplate;

    private OrderEventKafkaPublisher publisher;

    private final OrderEvent event = new OrderEvent(UUID.randomUUID(), 3L, "customer", "Cliente",
            OrderStatus.CREADO, OrderStatus.ACEPTADO, java.math.BigDecimal.TEN, Instant.now(),
            "operator", "Operador", java.util.List.of());

    @BeforeEach
    void setUp() {
        StreamingProperties properties = new StreamingProperties(new StreamingProperties.Topics(
                new StreamingProperties.Topic(TOPIC, 3, 3, Map.of()),
                new StreamingProperties.Topic(TOPIC + ".DLT", 3, 3, Map.of())));
        publisher = new OrderEventKafkaPublisher(kafkaTemplate, new OrderEventMessageMapper(), properties);
    }

    @Test
    void publicaEnElTopicoDeLasPropiedadesConKeyIgualAlOrderId() {
        ProducerRecord<String, OrderEventMessage> sent = new ProducerRecord<>(TOPIC, "3", null);
        SendResult<String, OrderEventMessage> result = new SendResult<>(sent,
                new RecordMetadata(new TopicPartition(TOPIC, 1), 0, 0, 0, 0, 0));
        when(kafkaTemplate.send(anyString(), anyString(), any(OrderEventMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(result));

        publisher.onOrderChanged(event);

        ArgumentCaptor<OrderEventMessage> message = ArgumentCaptor.forClass(OrderEventMessage.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("3"), message.capture());
        assertThat(message.getValue().eventId()).isEqualTo(event.eventId());
        assertThat(message.getValue().eventType()).isEqualTo("ORDER_STATUS_CHANGED");
    }

    @Test
    void noPropagaUnFalloSincronoDelEnvio() {
        when(kafkaTemplate.send(anyString(), anyString(), any(OrderEventMessage.class)))
                .thenThrow(new IllegalStateException("brokers caidos"));

        assertThatCode(() -> publisher.onOrderChanged(event)).doesNotThrowAnyException();
    }

    @Test
    void noPropagaUnFalloAsincronoDelEnvio() {
        when(kafkaTemplate.send(anyString(), anyString(), any(OrderEventMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("timeout")));

        assertThatCode(() -> publisher.onOrderChanged(event)).doesNotThrowAnyException();
    }
    @Test
    void publishesOnlyAfterCommit() {
        when(kafkaTemplate.send(anyString(), anyString(), any(OrderEventMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker no disponible")));
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(TransactionConfig.class);
            context.registerBean(OrderEventKafkaPublisher.class, () -> publisher);
            context.refresh();
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
            transaction.executeWithoutResult(status -> {
                context.publishEvent(event);
                verifyNoInteractions(kafkaTemplate);
            });
            verify(kafkaTemplate).send(eq(TOPIC), eq("3"), any(OrderEventMessage.class));
            clearInvocations(kafkaTemplate);
            transaction.executeWithoutResult(status -> {
                context.publishEvent(event);
                status.setRollbackOnly();
            });
            context.publishEvent(event);
            verifyNoInteractions(kafkaTemplate);
        } finally {
            database.shutdown();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfig {
    }
}
