package cl.duoc.pedidos360.orders.messaging.consumer.deadletter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import cl.duoc.pedidos360.orders.messaging.support.ManualAckHandler;

/**
 * Registra en el log los mensajes que llegan a una DLQ y los confirma. Asi ningun mensaje no
 * entregado se pierde en silencio: queda el motivo, la cola de origen, la routing key original,
 * el numero de intentos y el body.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterListener {

    private static final String DELIVERY_COUNT_HEADER = "x-delivery-count";

    private final ManualAckHandler ackHandler;

    @RabbitListener(queues = {"#{notificationsDeadLetterQueue.name}", "#{ticketsDeadLetterQueue.name}"})
    public void onDeadLetter(Message message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        ackHandler.handle(message, channel, deliveryTag, () -> logDeadLetter(message));
    }

    private void logDeadLetter(Message message) {
        MessageProperties props = message.getMessageProperties();
        Map<String, ?> death = firstDeath(props);
        log.error("Mensaje muerto en {}: messageId={} colaOrigen={} routingKeyOriginal={} motivo={} "
                        + "vecesMuerto={} entregas={} body={}",
                props.getConsumerQueue(), props.getMessageId(), death.get("queue"), death.get("routing-keys"),
                death.get("reason"), death.get("count"), props.getHeader(DELIVERY_COUNT_HEADER),
                new String(message.getBody(), StandardCharsets.UTF_8));
    }

    /** {@code x-death} lista una entrada por cola de origen; la primera es la mas reciente. */
    private Map<String, ?> firstDeath(MessageProperties props) {
        List<Map<String, ?>> deaths = props.getXDeathHeader();
        return deaths == null || deaths.isEmpty() ? Map.of() : deaths.get(0);
    }
}
