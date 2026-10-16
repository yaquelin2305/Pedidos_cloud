package cl.duoc.pedidos360.catalog.messaging.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import cl.duoc.pedidos360.catalog.event.StockLowEvent;
import cl.duoc.pedidos360.catalog.messaging.config.MessagingProperties;

@ExtendWith(MockitoExtension.class)
class StockEventPublisherTest {

    private static final String EXCHANGE = "pedidos360.catalog.events";
    private static final String ROUTING_KEY = "stock.low";

    @Mock
    private RabbitTemplate rabbitTemplate;

    private StockEventPublisher publisher;

    private final StockLowEvent event = new StockLowEvent(UUID.randomUUID(), 1L, "SKU-1", "Teclado",
            4, 5, Instant.now());

    @BeforeEach
    void setUp() {
        MessagingProperties properties = new MessagingProperties("pedidos360.dlx", 3,
                new MessagingProperties.Route(EXCHANGE, "pedidos360.catalog.stock-alerts",
                        "pedidos360.catalog.stock-alerts.dlq", ROUTING_KEY));
        publisher = new StockEventPublisher(rabbitTemplate, properties);
    }

    @Test
    void publicaEnElExchangeYRoutingKeyDeLasPropiedadesConMessageIdIgualAlEventId() {
        publisher.onStockLow(event);

        ArgumentCaptor<MessagePostProcessor> postProcessor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), eq(event),
                postProcessor.capture(), correlation.capture());

        Message sent = postProcessor.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertThat(sent.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        assertThat(correlation.getValue().getId()).isEqualTo(event.eventId().toString());
    }

    @Test
    void noPropagaLosErroresDelBroker() {
        doThrow(new AmqpException("broker caido")).when(rabbitTemplate).convertAndSend(anyString(), anyString(),
                any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));

        assertThatCode(() -> publisher.onStockLow(event)).doesNotThrowAnyException();
    }
}
