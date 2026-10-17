package cl.duoc.pedidos360.orders.messaging.consumer.ticket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import cl.duoc.pedidos360.orders.messaging.support.*;

@ExtendWith(MockitoExtension.class)
class OrderTicketListenerTest {
    private static final String VALID_EVENT = """
            {"eventId":"0b6f6c1e-8a43-4d5e-9f41-2f8f3f7a1c11","orderId":1,
             "customerId":"customer-1","customerName":"Maria","previousStatus":"CREADO",
             "status":"ACEPTADO","totalAmount":100,"occurredAt":"2026-10-16T20:00:00Z"}
            """;

    @Mock private Channel channel;
    @Mock private OrderTicketGenerator processor;
    private OrderTicketListener listener;

    @BeforeEach
    void setUp() {
        var reader = new OrderEventReader(new MessagePayloadReader(new ObjectMapper().findAndRegisterModules()));
        listener = new OrderTicketListener(new ManualAckHandler(), reader, processor);
    }

    @Test
    void acknowledgesSuccessfulProcessing() throws Exception {
        listener.onOrderAccepted(message(VALID_EVENT), channel, 7L);
        verify(processor).generate(any());
        verify(channel).basicAck(7L, false);
        verifyNoMoreInteractions(channel);
    }

    static Stream<String> invalidPayloads() {
        return Stream.of("{broken", "null", "{}", VALID_EVENT.replace("\"status\":\"ACEPTADO\"", "\"status\":\"INVALID\""),
                VALID_EVENT.replace("\"occurredAt\":\"2026-10-16T20:00:00Z\"", "\"occurredAt\":null"),
                VALID_EVENT.replace("\"previousStatus\":\"CREADO\"", "\"previousStatus\":null"));
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    void rejectsInvalidPayloadWithoutRequeue(String payload) throws Exception {
        listener.onOrderAccepted(message(payload), channel, 7L);
        verify(channel).basicNack(7L, false, false);
        verifyNoMoreInteractions(channel);
        verifyNoInteractions(processor);
    }

    @Test
    void requeuesRecoverableFailure() throws Exception {
        doThrow(new RecoverableProcessingException("fallo temporal", null)).when(processor).generate(any());
        listener.onOrderAccepted(message(VALID_EVENT), channel, 7L);
        verify(channel).basicNack(7L, false, true);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void requeuesUnexpectedRuntimeFailure() throws Exception {
        doThrow(new IllegalStateException("fallo inesperado")).when(processor).generate(any());
        listener.onOrderAccepted(message(VALID_EVENT), channel, 7L);
        verify(channel).basicNack(7L, false, true);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void rejectsIllegalArgumentWithoutRequeue() throws Exception {
        doThrow(new IllegalArgumentException("datos invalidos")).when(processor).generate(any());
        listener.onOrderAccepted(message(VALID_EVENT), channel, 7L);
        verify(channel).basicNack(7L, false, false);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void acknowledgesCorrectionWithoutGeneratingAnotherTicket() throws Exception {
        listener.onOrderAccepted(message(VALID_EVENT.replace("\"previousStatus\":\"CREADO\"",
                "\"previousStatus\":\"EN_PREPARACION\"")), channel, 7L);
        verify(channel).basicAck(7L, false);
        verifyNoMoreInteractions(channel);
        verifyNoInteractions(processor);
    }

    @Test
    void rejectsEventWithNonAcceptedStatus() throws Exception {
        listener.onOrderAccepted(message(VALID_EVENT.replace("\"status\":\"ACEPTADO\"",
                "\"status\":\"DESPACHADO\"")), channel, 7L);
        verify(channel).basicNack(7L, false, false);
        verifyNoInteractions(processor);
    }

    private Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("__TypeId__", "untrusted.Type");
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
