package cl.duoc.pedidos360.orders.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Linea de pedido tal como la envia el cliente: solo producto y cantidad. El nombre y el precio
 * se completan en el backend consultando a ms-pedidos360-catalog, nunca se confia en un precio
 * enviado desde el cliente.
 */
public record OrderItemRequestDTO(
        @NotNull Long productId,
        @NotNull @Positive Integer quantity) {
}
