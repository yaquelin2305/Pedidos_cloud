package cl.duoc.pedidos360.bff.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductRequestDTO(
        String sku,
        @NotBlank String name,
        String description,
        @NotNull @Positive BigDecimal price,
        @NotNull @PositiveOrZero Integer stock,
        String category) {
}
