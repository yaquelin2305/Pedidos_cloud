package cl.duoc.pedidos360.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Cuerpo de {@code PATCH /api/catalog/products/{id}/stock}: ajuste manual de stock desde la
 * tabla del front (Admin/Operator). A diferencia de {@link StockAdjustmentDTO}, el valor es el
 * stock final absoluto, no una cantidad a descontar.
 */
public record StockUpdateDTO(
        @NotNull @PositiveOrZero Integer stock) {
}
