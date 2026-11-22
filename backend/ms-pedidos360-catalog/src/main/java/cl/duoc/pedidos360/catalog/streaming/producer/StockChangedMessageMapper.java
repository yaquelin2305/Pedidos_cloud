package cl.duoc.pedidos360.catalog.streaming.producer;

import org.springframework.stereotype.Component;

import cl.duoc.pedidos360.catalog.event.StockChangedEvent;
import cl.duoc.pedidos360.catalog.streaming.message.StockChangedMessage;

/**
 * Convierte el evento de dominio en el mensaje de Kafka y define su key.
 */
@Component
public class StockChangedMessageMapper {

    static final int SCHEMA_VERSION = 1;
    static final String SOURCE = "ms-pedidos360-catalog";

    public StockChangedMessage toMessage(StockChangedEvent event) {
        return new StockChangedMessage(
                event.eventId(),
                event.changeType().name(),
                SCHEMA_VERSION,
                SOURCE,
                event.occurredAt(),
                event.productId(),
                event.sku(),
                event.name(),
                event.previousStock(),
                event.stock(),
                event.lowStockThreshold(),
                event.stock() <= event.lowStockThreshold(),
                event.active());
    }

    /**
     * La key es el producto: todos sus cambios caen en la misma particion, conservan su orden y la
     * compactacion deja solo el ultimo.
     */
    public String keyOf(StockChangedMessage message) {
        return String.valueOf(message.productId());
    }
}
