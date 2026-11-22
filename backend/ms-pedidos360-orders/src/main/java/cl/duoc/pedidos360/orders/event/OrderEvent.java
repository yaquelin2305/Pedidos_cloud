package cl.duoc.pedidos360.orders.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

import cl.duoc.pedidos360.orders.entity.OrderStatus;

/** Instantanea del cambio confirmado; previousStatus es null solo al crear. */
public record OrderEvent(UUID eventId, Long orderId, String customerId, String customerName,
                         OrderStatus previousStatus, OrderStatus status, BigDecimal totalAmount,
                         Instant occurredAt, String actorId, String actorName, List<OrderEventItem> items) {
}
