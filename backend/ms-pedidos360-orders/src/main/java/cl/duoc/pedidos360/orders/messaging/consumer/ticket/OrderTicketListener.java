package cl.duoc.pedidos360.orders.messaging.consumer.ticket;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;
import cl.duoc.pedidos360.orders.entity.OrderStatus;
import cl.duoc.pedidos360.orders.event.OrderEvent;
import cl.duoc.pedidos360.orders.messaging.support.InvalidMessageException;
import cl.duoc.pedidos360.orders.messaging.support.ManualAckHandler;
import cl.duoc.pedidos360.orders.messaging.support.OrderEventReader;
import lombok.RequiredArgsConstructor;

/** Confirma las correcciones sin emitir comprobante; solo la primera aceptacion lo genera. */
@Component
@RequiredArgsConstructor
public class OrderTicketListener {
    private final ManualAckHandler ackHandler;
    private final OrderEventReader eventReader;
    private final OrderTicketGenerator ticketGenerator;

    @RabbitListener(queues = "#{ticketsQueue.name}")
    public void onOrderAccepted(Message message, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        ackHandler.handle(message, channel, deliveryTag, () -> {
            OrderEvent event = eventReader.read(message);
            if (event.status() != OrderStatus.ACEPTADO) {
                throw new InvalidMessageException("El comprobante requiere un pedido ACEPTADO");
            }
            if (event.previousStatus() == OrderStatus.CREADO) {
                ticketGenerator.generate(event);
            }
        });
    }
}
