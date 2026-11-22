package cl.duoc.pedidos360.audit.streaming.message;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Linea de un pedido dentro de {@link OrderEventMessage}.
 */
public record OrderLineMessage(
        @NotNull Long productId,
        String productName,
        @PositiveOrZero Integer quantity,
        @PositiveOrZero BigDecimal unitPrice,
        @PositiveOrZero BigDecimal subtotal) {
}
