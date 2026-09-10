package cl.duoc.pedidos360.orders.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import cl.duoc.pedidos360.orders.entity.Order;
import cl.duoc.pedidos360.orders.entity.OrderStatus;

/**
 * Contrato de salida. La entidad Order nunca se serializa directamente: entre otras cosas,
 * evita exponer la referencia inversa Order<->OrderItem tal cual la mapea JPA.
 */
public record OrderResponseDTO(
        Long id,
        String customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant acceptedAt,
        Instant dispatchedAt,
        Instant deliveredAt,
        List<OrderItemDTO> items) {

    public static OrderResponseDTO from(Order order) {
        return new OrderResponseDTO(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getAcceptedAt(),
                order.getDispatchedAt(),
                order.getDeliveredAt(),
                order.getItems().stream().map(OrderItemDTO::from).toList());
    }
}
