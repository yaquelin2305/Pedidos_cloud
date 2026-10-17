package cl.duoc.pedidos360.orders.messaging.support;

import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import cl.duoc.pedidos360.orders.entity.OrderStatus;
import cl.duoc.pedidos360.orders.event.OrderEvent;
import lombok.RequiredArgsConstructor;

/** Valida el contrato compartido antes de ejecutar cualquier consumidor de pedidos. */
@Component
@RequiredArgsConstructor
public class OrderEventReader {
    private final MessagePayloadReader payloadReader;

    public OrderEvent read(Message message) {
        OrderEvent event = payloadReader.require(payloadReader.read(message, OrderEvent.class), "payload");
        payloadReader.require(event.eventId(), "eventId");
        payloadReader.require(event.orderId(), "orderId");
        payloadReader.require(event.customerId(), "customerId");
        payloadReader.require(event.customerName(), "customerName");
        payloadReader.require(event.status(), "status");
        payloadReader.require(event.totalAmount(), "totalAmount");
        payloadReader.require(event.occurredAt(), "occurredAt");
        if (event.status() != OrderStatus.CREADO) {
            payloadReader.require(event.previousStatus(), "previousStatus");
        }
        return event;
    }
}
