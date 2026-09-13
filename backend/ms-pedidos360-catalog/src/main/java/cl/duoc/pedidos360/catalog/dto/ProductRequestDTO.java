package cl.duoc.pedidos360.catalog.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Contrato de entrada para crear o actualizar un producto. El stock inicial se
 * declara aqui, pero luego solo se modifica a traves de decreaseStock/updateStock (nunca con
 * PUT), para que los cambios de stock queden siempre auditables y transaccionales.
 * sku es opcional: si viene vacio, el Service lo autogenera.
 */
public record ProductRequestDTO(
        String sku,
        @NotBlank String name,
        String description,
        @NotNull @Positive BigDecimal price,
        @NotNull @PositiveOrZero Integer stock,
        String category) {
}
