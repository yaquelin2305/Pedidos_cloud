package cl.duoc.pedidos360.orders.messaging.producer;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cl.duoc.pedidos360.orders.event.OrderEvent;
import cl.duoc.pedidos360.orders.messaging.config.MessagingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Publica solo tras el commit; una caida del broker no revierte el pedido ya guardado. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(OrderEvent event) {
        String routingKey = properties.routingKeys().get(event.status());
        String messageId = event.eventId().toString();
        try {
            rabbitTemplate.convertAndSend(properties.exchange(), routingKey, event, message -> {
                message.getMessageProperties().setMessageId(messageId);
                return message;
            }, new CorrelationData(messageId));
            log.info("Publicado evento {} del pedido {} en {} [{}]",
                    messageId, event.orderId(), properties.exchange(), routingKey);
        } catch (AmqpException ex) {
            log.error("No se pudo publicar el evento {} del pedido {}: {}",
                    messageId, event.orderId(), ex.getMessage());
        }
    }
}
