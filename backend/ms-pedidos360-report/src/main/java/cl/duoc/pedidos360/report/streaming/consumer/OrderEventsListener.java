package cl.duoc.pedidos360.report.streaming.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.report.service.ReportProjectionService;
import cl.duoc.pedidos360.report.service.EventOrigin;
import cl.duoc.pedidos360.report.streaming.message.OrderEventMessage;
import cl.duoc.pedidos360.report.streaming.support.EventValidator;

/**
 * Consume los eventos de pedidos: valida el payload y delega en {@link ReportProjectionService}. Los errores
 * los resuelve el error handler del contenedor (reintentos y DLT).
 */
@Component
@RequiredArgsConstructor
public class OrderEventsListener {

    private final EventValidator eventValidator;
    private final ReportProjectionService reportProjectionService;

    @KafkaListener(topics = "${pedidos360.streaming.topics.order-events}",
            containerFactory = "orderEventListenerContainerFactory")
    public void onOrderEvent(@Payload(required = false) OrderEventMessage message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        reportProjectionService.recordOrderEvent(eventValidator.validate(message), new EventOrigin(topic, partition, offset));
    }
}
