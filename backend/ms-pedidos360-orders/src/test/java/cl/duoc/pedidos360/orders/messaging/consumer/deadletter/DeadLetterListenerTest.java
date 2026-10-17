package cl.duoc.pedidos360.orders.messaging.consumer.deadletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.rabbitmq.client.Channel;
import cl.duoc.pedidos360.orders.messaging.support.ManualAckHandler;

@ExtendWith(OutputCaptureExtension.class)
class DeadLetterListenerTest {
    @ParameterizedTest
    @ValueSource(strings = {"notifications", "tickets"})
    void logsOriginReasonAttemptsAndBodyBeforeAcknowledging(String route, CapturedOutput output) throws Exception {
        Channel channel = mock(Channel.class);
        MessageProperties properties = new MessageProperties();
        String queue = "pedidos360.orders." + route;
        properties.setConsumerQueue(queue + ".dlq");
        properties.setHeader("x-delivery-count", 3L);
        properties.setHeader("x-death", List.of(Map.of("queue", queue, "reason", "delivery_limit",
                "count", 1L, "routing-keys", List.of("order.accepted"))));
        Message message = new Message("{broken".getBytes(StandardCharsets.UTF_8), properties);

        new DeadLetterListener(new ManualAckHandler()).onDeadLetter(message, channel, 9L);

        assertThat(output).contains(queue, "order.accepted", "delivery_limit", "entregas=3", "{broken");
        verify(channel).basicAck(9L, false);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void acknowledgesEvenWithoutDeathHeaders() throws Exception {
        Channel channel = mock(Channel.class);
        new DeadLetterListener(new ManualAckHandler()).onDeadLetter(
                new Message(new byte[0], new MessageProperties()), channel, 9L);
        verify(channel).basicAck(9L, false);
    }
}
