package cl.duoc.pedidos360.orders.streaming.producer;

import org.springframework.stereotype.Component;

import cl.duoc.pedidos360.orders.entity.OrderStatus;
import cl.duoc.pedidos360.orders.event.OrderEvent;
import cl.duoc.pedidos360.orders.streaming.message.OrderEventMessage;
import cl.duoc.pedidos360.orders.streaming.message.OrderLineMessage;

@Component
public class OrderEventMessageMapper {

    public OrderEventMessage toMessage(OrderEvent event) {
        String eventType = event.previousStatus() == null ? "ORDER_CREATED"
                : event.status() == OrderStatus.CANCELADO ? "ORDER_CANCELLED" : "ORDER_STATUS_CHANGED";
        return new OrderEventMessage(event.eventId(), eventType, 1, "ms-pedidos360-orders",
                event.occurredAt(), event.orderId(), event.customerId(), event.customerName(),
                event.actorId(), event.actorName(),
                event.previousStatus() == null ? null : event.previousStatus().name(), event.status().name(),
                event.totalAmount(), event.items().stream().map(item -> new OrderLineMessage(item.productId(),
                        item.productName(), item.quantity(), item.unitPrice(), item.subtotal())).toList());
    }

    /** La key por pedido mantiene todos sus cambios en la misma particion y en orden. */
    public String keyOf(OrderEventMessage message) {
        return String.valueOf(message.orderId());
    }
}
