package cl.duoc.pedidos360.catalog.streaming.producer;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import cl.duoc.pedidos360.catalog.event.StockChangedEvent;
import cl.duoc.pedidos360.catalog.streaming.config.StreamingProperties;
import cl.duoc.pedidos360.catalog.streaming.message.StockChangedMessage;

/**
 * Publica cada {@link StockChangedEvent} en el topico de stock de Kafka.
 *
 * <p>Escucha despues del commit: si la transaccion del producto se revierte, no se publica nada. El
 * envio es asincrono y su resultado se registra en el callback. Nunca propaga excepciones: los
 * callbacks {@code afterCommit} no las aislan, asi que un fallo aqui llegaria al cliente HTTP con el
 * cambio ya guardado y podria impedir que corra el publicador de RabbitMQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockChangedKafkaPublisher {

    private final KafkaTemplate<String, StockChangedMessage> kafkaTemplate;
    private final StockChangedMessageMapper mapper;
    private final StreamingProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent event) {
        String topic = properties.topics().stock().name();
        StockChangedMessage message = mapper.toMessage(event);
        String key = mapper.keyOf(message);
        try {
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
            logFailure(topic, key, message, ex);
        }
    }

    private void logFailure(String topic, String key, StockChangedMessage message, Throwable cause) {
        log.error("No se pudo publicar {} {} en {} [key={}]: {}", message.eventType(), message.eventId(),
                topic, key, cause.getMessage());
    }
}
