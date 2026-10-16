package cl.duoc.pedidos360.catalog.messaging.consumer.deadletter;

import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.rabbitmq.client.Channel;

import cl.duoc.pedidos360.catalog.messaging.support.ManualAckHandler;

@ExtendWith(MockitoExtension.class)
class DeadLetterListenerTest {

    @Mock
    private Channel channel;

    @Test
    void registraYConfirmaElMensajeMuerto() throws Exception {
        MessageProperties props = new MessageProperties();
        props.setConsumerQueue("pedidos360.catalog.stock-alerts.dlq");
        props.setHeader("x-death", List.of(Map.of(
                "queue", "pedidos360.catalog.stock-alerts",
                "reason", "rejected",
                "count", 1L,
                "routing-keys", List.of("stock.low"))));
        Message message = new Message("{json roto".getBytes(StandardCharsets.UTF_8), props);

        new DeadLetterListener(new ManualAckHandler()).onDeadLetter(message, channel, 3L);

        verify(channel).basicAck(3L, false);
    }

    @Test
    void confirmaAunqueFalteElHeaderXDeath() throws Exception {
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        new DeadLetterListener(new ManualAckHandler()).onDeadLetter(message, channel, 4L);

        verify(channel).basicAck(4L, false);
    }
}
