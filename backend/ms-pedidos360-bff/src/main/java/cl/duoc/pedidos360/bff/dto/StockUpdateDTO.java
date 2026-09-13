package cl.duoc.pedidos360.bff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StockUpdateDTO(
        @NotNull @PositiveOrZero Integer stock) {
}
