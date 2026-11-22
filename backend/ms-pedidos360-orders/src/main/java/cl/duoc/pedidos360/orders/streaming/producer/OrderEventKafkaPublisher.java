package cl.duoc.pedidos360.orders.streaming.producer;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import cl.duoc.pedidos360.orders.event.OrderEvent;
import cl.duoc.pedidos360.orders.streaming.config.StreamingProperties;
import cl.duoc.pedidos360.orders.streaming.message.OrderEventMessage;

/**
 * Publica cada {@link OrderEvent} en el topico de pedidos de Kafka.
 *
 * <p>Escucha despues del commit: si la transaccion del pedido se revierte, no se publica nada. El
 * envio es asincrono y su resultado se registra en el callback. Nunca propaga excepciones: los
 * callbacks {@code afterCommit} no las aislan, asi que un fallo aqui llegaria al cliente HTTP con el
 * cambio ya guardado y podria impedir que corra el publicador de RabbitMQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventKafkaPublisher {

    private final KafkaTemplate<String, OrderEventMessage> kafkaTemplate;
    private final OrderEventMessageMapper mapper;
    private final StreamingProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(OrderEvent event) {
        String topic = properties.topics().orderEvents().name();
        String key = String.valueOf(event.orderId());
        try {
            OrderEventMessage message = mapper.toMessage(event);
            kafkaTemplate.send(topic, key, message).whenComplete((result, ex) -> {
                if (ex != null) {
                    logFailure(topic, key, message, ex);
                    return;
                }
                RecordMetadata metadata = result.getRecordMetadata();
                log.info("Publicado {} {} en {} particion {} offset {} [key={}]", message.eventType(),
                        message.eventId(), metadata.topic(), metadata.partition(), metadata.offset(), key);
            });
        } catch (RuntimeException ex) {
            log.error("No se pudo publicar evento {} en {} [key={}]: {}",
                    event.eventId(), topic, key, ex.getMessage());
        }
    }

    private void logFailure(String topic, String key, OrderEventMessage message, Throwable cause) {
        log.error("No se pudo publicar {} {} en {} [key={}]: {}", message.eventType(), message.eventId(),
                topic, key, cause.getMessage());
    }
}
