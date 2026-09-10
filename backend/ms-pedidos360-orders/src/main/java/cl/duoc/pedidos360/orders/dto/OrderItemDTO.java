package cl.duoc.pedidos360.orders.dto;

import java.math.BigDecimal;

import cl.duoc.pedidos360.orders.entity.OrderItem;

public record OrderItemDTO(
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {

    public static OrderItemDTO from(OrderItem item) {
        return new OrderItemDTO(item.getProductId(), item.getProductName(), item.getQuantity(),
                item.getUnitPrice(), item.getSubtotal());
    }
}
