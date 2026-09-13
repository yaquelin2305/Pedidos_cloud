package cl.duoc.pedidos360.bff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequestDTO(
        @NotNull Long productId,
        @NotNull @Positive Integer quantity) {
}
