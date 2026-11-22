package cl.duoc.pedidos360.audit.streaming.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.audit.service.AuditService;
import cl.duoc.pedidos360.audit.service.EventOrigin;
import cl.duoc.pedidos360.audit.streaming.message.StockChangedMessage;
import cl.duoc.pedidos360.audit.streaming.support.EventValidator;

/**
 * Consume los cambios de stock: valida el payload y delega en {@link AuditService}. Los errores los
 * resuelve el error handler del contenedor (reintentos y DLT).
 */
@Component
@RequiredArgsConstructor
public class StockListener {

    private final EventValidator eventValidator;
    private final AuditService auditService;

    @KafkaListener(topics = "${pedidos360.streaming.topics.stock}",
            containerFactory = "stockListenerContainerFactory")
    public void onStockChanged(@Payload(required = false) StockChangedMessage message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        auditService.recordStockEvent(eventValidator.validate(message), new EventOrigin(topic, partition, offset));
    }
}
