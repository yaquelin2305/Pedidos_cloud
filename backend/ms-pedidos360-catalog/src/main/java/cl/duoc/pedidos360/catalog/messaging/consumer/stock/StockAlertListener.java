package cl.duoc.pedidos360.catalog.messaging.consumer.stock;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.catalog.event.StockLowEvent;
import cl.duoc.pedidos360.catalog.messaging.support.ManualAckHandler;
import cl.duoc.pedidos360.catalog.messaging.support.MessagePayloadReader;

/**
 * Consume las alertas de stock bajo. Valida el payload y delega la alerta en
 * {@link RestockAlertNotifier}; el ACK o NACK lo decide {@link ManualAckHandler}.
 */
@Component
@RequiredArgsConstructor
public class StockAlertListener {

    private final ManualAckHandler ackHandler;
    private final MessagePayloadReader payloadReader;
    private final RestockAlertNotifier restockAlertNotifier;

    @RabbitListener(queues = "#{stockAlertsQueue.name}")
    public void onStockLow(Message message, Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        ackHandler.handle(message, channel, deliveryTag, () -> {
            StockLowEvent event = payloadReader.read(message, StockLowEvent.class);
            payloadReader.require(event.eventId(), "eventId");
            payloadReader.require(event.productId(), "productId");
            payloadReader.require(event.sku(), "sku");
            payloadReader.require(event.stock(), "stock");
            payloadReader.require(event.threshold(), "threshold");
            restockAlertNotifier.notifyRestock(event);
        });
    }
}
