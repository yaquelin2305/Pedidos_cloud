package cl.duoc.pedidos360.orders.event;

import java.math.BigDecimal;

public record OrderEventItem(Long productId, String productName, Integer quantity,
                             BigDecimal unitPrice, BigDecimal subtotal) {
}
