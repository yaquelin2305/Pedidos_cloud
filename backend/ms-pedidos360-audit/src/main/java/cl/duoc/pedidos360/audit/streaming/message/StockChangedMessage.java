package cl.duoc.pedidos360.audit.streaming.message;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Mensaje del topico {@code pedidos360.catalog.stock} (key = {@code productId}). Copia propia del
 * contrato que publica ms-pedidos360-catalog; las anotaciones validan el payload al consumirlo.
 *
 * @param previousStock {@code null} al crear el producto
 */
public record StockChangedMessage(
        @NotNull UUID eventId,
        @NotNull String eventType,
        Integer schemaVersion,
        String source,
        @NotNull Instant occurredAt,
        @NotNull Long productId,
        String sku,
        String name,
        Integer previousStock,
        @NotNull @PositiveOrZero Integer stock,
        Integer lowStockThreshold,
        boolean lowStock,
        @NotNull Boolean active) {
}
