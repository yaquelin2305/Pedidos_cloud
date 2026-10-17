package cl.duoc.pedidos360.orders.messaging.consumer.notification;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;
import cl.duoc.pedidos360.orders.messaging.support.ManualAckHandler;
import cl.duoc.pedidos360.orders.messaging.support.OrderEventReader;
import lombok.RequiredArgsConstructor;

/** Orquesta lectura, notificacion y respuesta manual al broker. */
@Component
@RequiredArgsConstructor
public class OrderNotificationListener {
    private final ManualAckHandler ackHandler;
    private final OrderEventReader eventReader;
    private final OrderNotificationNotifier notifier;

    @RabbitListener(queues = "#{notificationsQueue.name}")
    public void onOrderChanged(Message message, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        ackHandler.handle(message, channel, deliveryTag, () -> notifier.notifyCustomer(eventReader.read(message)));
    }
}
