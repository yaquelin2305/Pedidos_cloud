package cl.duoc.pedidos360.catalog.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: cambio el estado vigente de un producto (alta, edicion, stock o baja).
 * Es neutro respecto del transporte; {@code streaming.producer} decide como y donde se publica.
 *
 * @param previousStock stock antes del cambio; {@code null} al crear el producto
 */
public record StockChangedEvent(
        UUID eventId,
        StockChangeType changeType,
        Long productId,
        String sku,
        String name,
        Integer previousStock,
        Integer stock,
        Integer lowStockThreshold,
        boolean active,
        Instant occurredAt) {
}
