package cl.duoc.pedidos360.catalog.messaging.producer;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import cl.duoc.pedidos360.catalog.event.StockLowEvent;
import cl.duoc.pedidos360.catalog.messaging.config.MessagingProperties;

/**
 * Traduce el evento de dominio {@link StockLowEvent} a un mensaje de RabbitMQ.
 *
 * <p>Escucha despues del commit: si la transaccion del producto se revierte, no se publica nada.
 * Un fallo del broker se registra y no se propaga, porque el cambio de stock ya quedo guardado y
 * no debe convertirse en un error HTTP para quien lo hizo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockLow(StockLowEvent event) {
        MessagingProperties.Route stock = properties.stock();
        String messageId = event.eventId().toString();
        try {
            rabbitTemplate.convertAndSend(stock.exchange(), stock.routingKey(), event, message -> {
                message.getMessageProperties().setMessageId(messageId);
                return message;
            }, new CorrelationData(messageId));
            log.info("Publicada alerta de stock bajo {} del producto {} en {} [{}]",
                    messageId, event.productId(), stock.exchange(), stock.routingKey());
        } catch (AmqpException ex) {
            log.error("No se pudo publicar la alerta de stock bajo {} del producto {}: {}",
                    messageId, event.productId(), ex.getMessage());
        }
    }
}
