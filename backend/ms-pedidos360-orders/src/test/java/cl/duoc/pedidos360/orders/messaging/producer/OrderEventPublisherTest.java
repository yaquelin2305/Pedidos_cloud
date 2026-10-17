package cl.duoc.pedidos360.orders.messaging.producer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import cl.duoc.pedidos360.orders.entity.OrderStatus;
import cl.duoc.pedidos360.orders.event.OrderEvent;
import cl.duoc.pedidos360.orders.messaging.config.MessagingProperties;

class OrderEventPublisherTest {
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final MessagingProperties properties = properties();
    private final OrderEventPublisher publisher = new OrderEventPublisher(rabbitTemplate, properties);

    private MessagingProperties properties() {
        var keys = new EnumMap<OrderStatus, String>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            keys.put(status, "custom." + status.name());
        }
        return new MessagingProperties("custom.exchange", "custom.dlx", 3, null, null, keys);
    }

    private OrderEvent event(OrderStatus status) {
        return new OrderEvent(UUID.randomUUID(), 1L, "customer-1", "Maria", OrderStatus.CREADO,
                status, BigDecimal.TEN, Instant.now());
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void usesConfiguredDestinationAndEventId(OrderStatus status) {
        OrderEvent event = event(status);
        publisher.onOrderChanged(event);

        var postProcessor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        var correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(eq("custom.exchange"), eq(properties.routingKeys().get(status)),
                eq(event), postProcessor.capture(), correlation.capture());
        Message message = postProcessor.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        assertThat(correlation.getValue().getId()).isEqualTo(event.eventId().toString());
    }

    @Test
    void doesNotPropagateBrokerFailure() {
        doThrow(new AmqpException("broker caido")).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        assertThatCode(() -> publisher.onOrderChanged(event(OrderStatus.ACEPTADO))).doesNotThrowAnyException();
    }

    @Test
    void sendsOnlyAfterCommitAndNeverAfterRollbackOrOutsideTransaction() {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(TransactionConfig.class);
            context.registerBean(OrderEventPublisher.class, () -> publisher);
            context.refresh();
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
            OrderEvent committed = event(OrderStatus.ACEPTADO);

            transaction.executeWithoutResult(status -> {
                context.publishEvent(committed);
                verifyNoInteractions(rabbitTemplate);
            });
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), eq(committed),
                    any(MessagePostProcessor.class), any(CorrelationData.class));
            clearInvocations(rabbitTemplate);

            transaction.executeWithoutResult(status -> {
                context.publishEvent(event(OrderStatus.CANCELADO));
                status.setRollbackOnly();
            });
            context.publishEvent(event(OrderStatus.CREADO));
            verifyNoInteractions(rabbitTemplate);
        } finally {
            database.shutdown();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfig {
    }
}
