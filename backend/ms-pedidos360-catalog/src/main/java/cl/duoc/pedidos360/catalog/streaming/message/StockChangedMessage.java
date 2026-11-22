package cl.duoc.pedidos360.catalog.streaming.message;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensaje del topico {@code pedidos360.catalog.stock}, con key = {@code productId}. Es el snapshot
 * vigente del producto: el topico es compactado y conserva el ultimo mensaje de cada key.
 *
 * @param eventId           identidad unica; los consumidores descartan duplicados con ella
 * @param eventType         valor de {@code StockChangeType}: que cambio
 * @param schemaVersion     version del contrato, para evolucionarlo sin romper consumidores
 * @param source            servicio que origino el evento
 * @param occurredAt        momento del cambio
 * @param productId         agregado; tambien es la key de compactacion
 * @param sku               identifica el producto sin consultar catalog
 * @param name              identifica el producto sin consultar catalog
 * @param previousStock     stock antes del cambio; {@code null} al crear
 * @param stock             stock vigente, el valor que conserva la compactacion
 * @param lowStockThreshold umbral vigente en catalog, para que el consumidor aplique la misma regla
 * @param lowStock          {@code stock <= lowStockThreshold}, lectura directa para alertas
 * @param active            un producto desactivado sale del inventario vigente
 */
public record StockChangedMessage(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String source,
        Instant occurredAt,
        Long productId,
        String sku,
        String name,
        Integer previousStock,
        Integer stock,
        Integer lowStockThreshold,
        boolean lowStock,
        boolean active) {
}
