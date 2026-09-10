package cl.duoc.pedidos360.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo del endpoint de descuento de stock. El id del producto se toma de la
 * ruta ({@code /products/{id}/stock/decrease}); productId es opcional aqui y solo
 * se valida como chequeo cruzado cuando el llamador (ms-pedidos360-orders) lo envia.
 */
public record StockAdjustmentDTO(
        Long productId,
        @NotNull @Positive Integer quantity) {
}
