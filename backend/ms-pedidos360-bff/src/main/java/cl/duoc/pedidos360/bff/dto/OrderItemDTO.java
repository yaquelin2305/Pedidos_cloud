package cl.duoc.pedidos360.bff.dto;

import java.math.BigDecimal;

public record OrderItemDTO(
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {
}
