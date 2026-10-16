package cl.duoc.pedidos360.catalog.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: el stock de un producto acaba de quedar en o bajo el umbral de reposicion.
 * Es neutro respecto del transporte; {@code messaging.producer} decide como y donde se publica.
 */
public record StockLowEvent(
        UUID eventId,
        Long productId,
        String sku,
        String name,
        Integer stock,
        Integer threshold,
        Instant occurredAt) {
}
