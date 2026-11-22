package cl.duoc.pedidos360.catalog.streaming.producer;

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
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import cl.duoc.pedidos360.catalog.event.StockChangeType;
import cl.duoc.pedidos360.catalog.event.StockChangedEvent;
import cl.duoc.pedidos360.catalog.streaming.config.StreamingProperties;
import cl.duoc.pedidos360.catalog.streaming.message.StockChangedMessage;

@ExtendWith(MockitoExtension.class)
class StockChangedKafkaPublisherTest {

    private static final String TOPIC = "pedidos360.catalog.stock";

    @Mock
    private KafkaTemplate<String, StockChangedMessage> kafkaTemplate;

    private StockChangedKafkaPublisher publisher;

    private final StockChangedEvent event = new StockChangedEvent(UUID.randomUUID(), StockChangeType.STOCK_ADJUSTED,
            3L, "SKU-3", "Monitor", 10, 4, 5, true, Instant.now());

    @BeforeEach
    void setUp() {
        StreamingProperties properties = new StreamingProperties(new StreamingProperties.Topics(
                new StreamingProperties.Topic(TOPIC, 3, 3, Map.of()),
                new StreamingProperties.Topic(TOPIC + ".DLT", 3, 3, Map.of())));
        publisher = new StockChangedKafkaPublisher(kafkaTemplate, new StockChangedMessageMapper(), properties);
    }

    @Test
    void publicaEnElTopicoDeLasPropiedadesConKeyIgualAlProductId() {
        ProducerRecord<String, StockChangedMessage> sent = new ProducerRecord<>(TOPIC, "3", null);
        SendResult<String, StockChangedMessage> result = new SendResult<>(sent,
                new RecordMetadata(new TopicPartition(TOPIC, 1), 0, 0, 0, 0, 0));
        when(kafkaTemplate.send(anyString(), anyString(), any(StockChangedMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(result));

        publisher.onStockChanged(event);

        ArgumentCaptor<StockChangedMessage> message = ArgumentCaptor.forClass(StockChangedMessage.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("3"), message.capture());
        assertThat(message.getValue().eventId()).isEqualTo(event.eventId());
        assertThat(message.getValue().lowStock()).isTrue();
    }

    @Test
    void noPropagaUnFalloSincronoDelEnvio() {
        when(kafkaTemplate.send(anyString(), anyString(), any(StockChangedMessage.class)))
                .thenThrow(new KafkaException("brokers caidos"));

        assertThatCode(() -> publisher.onStockChanged(event)).doesNotThrowAnyException();
    }

    @Test
    void noPropagaUnFalloAsincronoDelEnvio() {
        when(kafkaTemplate.send(anyString(), anyString(), any(StockChangedMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("timeout")));

        assertThatCode(() -> publisher.onStockChanged(event)).doesNotThrowAnyException();
    }
}
