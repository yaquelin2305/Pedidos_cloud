package cl.duoc.pedidos360.catalog.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Contrato de entrada para crear o actualizar un producto. El stock inicial se
 * declara aqui, pero luego solo se modifica a traves de decreaseStock (nunca con PUT),
 * para que el descuento por pedidos sea siempre auditable y transaccional.
 */
public record ProductRequestDTO(
        @NotBlank String sku,
        @NotBlank String name,
        String description,
        @NotNull @Positive BigDecimal price,
        @NotNull @PositiveOrZero Integer stock,
        String category) {
}
