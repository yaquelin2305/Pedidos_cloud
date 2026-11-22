package cl.duoc.pedidos360.audit.streaming.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

import cl.duoc.pedidos360.audit.streaming.support.InvalidEventException;

/**
 * Verifica la politica de errores: un payload invalido va a la DLT de su topico y en la misma particion
 * sin reintentos, mientras que un error transitorio se reintenta antes de recuperarse.
 */
class KafkaErrorHandlingConfigTest {

    private static final String TOPIC = "pedidos360.orders.events";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<Object, Object> deadLetterTemplate = mock(KafkaTemplate.class);

    private DefaultErrorHandler errorHandler;

    private final ConsumerRecord<Object, Object> record = new ConsumerRecord<>(TOPIC, 2, 40L, "12", "{}");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ProducerFactory<Object, Object> producerFactory = mock(ProducerFactory.class);
        when(producerFactory.getConfigurationProperties()).thenReturn(Map.of());
        when(deadLetterTemplate.getProducerFactory()).thenReturn(producerFactory);
        when(deadLetterTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.getConsumer().setGroupId("pedidos360-audit");
        StreamingProperties streamingProperties = new StreamingProperties(
                new StreamingProperties.Topics(TOPIC, "pedidos360.catalog.stock"), ".DLT", 1,
                new StreamingProperties.Retry(0, 3));
        errorHandler = new KafkaErrorHandlingConfig()
                .kafkaErrorHandler(deadLetterTemplate, streamingProperties, kafkaProperties);
    }

    private boolean handle(Exception ex) {
        return errorHandler.handleOne(ex, record, mock(Consumer.class), mock(MessageListenerContainer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unPayloadInvalidoVaALaDltDeInmediatoEnLaMismaParticion() {
        boolean recovered = handle(new InvalidEventException("Payload invalido: eventId must not be null"));

        assertThat(recovered).isTrue();
        ArgumentCaptor<ProducerRecord<Object, Object>> sent =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(deadLetterTemplate).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo(TOPIC + ".DLT");
        assertThat(sent.getValue().partition()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unErrorTransitorioSeReintentaAntesDeIrALaDlt() {
        IllegalStateException transientError = new IllegalStateException("base de datos no disponible");

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(handle(transientError)).isFalse();
        }
        verify(deadLetterTemplate, never()).send(any(ProducerRecord.class));

        assertThat(handle(transientError)).isTrue();
        verify(deadLetterTemplate).send(any(ProducerRecord.class));
    }
}
