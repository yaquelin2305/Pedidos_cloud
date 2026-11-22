package cl.duoc.pedidos360.catalog.streaming.producer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import cl.duoc.pedidos360.catalog.event.StockChangeType;
import cl.duoc.pedidos360.catalog.event.StockChangedEvent;
import cl.duoc.pedidos360.catalog.streaming.message.StockChangedMessage;

class StockChangedMessageMapperTest {

    private final StockChangedMessageMapper mapper = new StockChangedMessageMapper();

    private StockChangedEvent event(int stock, int threshold) {
        return new StockChangedEvent(UUID.randomUUID(), StockChangeType.STOCK_DECREASED, 7L, "SKU-7", "Mouse",
                10, stock, threshold, true, Instant.parse("2026-11-21T12:00:00Z"));
    }

    @Test
    void copiaLosCamposDelEventoYAgregaMetadatos() {
        StockChangedEvent event = event(6, 5);

        StockChangedMessage message = mapper.toMessage(event);

        assertThat(message.eventId()).isEqualTo(event.eventId());
        assertThat(message.eventType()).isEqualTo("STOCK_DECREASED");
        assertThat(message.schemaVersion()).isEqualTo(1);
        assertThat(message.source()).isEqualTo("ms-pedidos360-catalog");
        assertThat(message.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(message.productId()).isEqualTo(7L);
        assertThat(message.sku()).isEqualTo("SKU-7");
        assertThat(message.name()).isEqualTo("Mouse");
        assertThat(message.previousStock()).isEqualTo(10);
        assertThat(message.stock()).isEqualTo(6);
        assertThat(message.lowStockThreshold()).isEqualTo(5);
        assertThat(message.active()).isTrue();
    }

    @Test
    void marcaStockBajoCuandoQuedaEnOBajoElUmbral() {
        assertThat(mapper.toMessage(event(5, 5)).lowStock()).isTrue();
        assertThat(mapper.toMessage(event(0, 5)).lowStock()).isTrue();
    }

    @Test
    void noMarcaStockBajoSobreElUmbral() {
        assertThat(mapper.toMessage(event(6, 5)).lowStock()).isFalse();
    }

    @Test
    void laKeyEsElProductId() {
        assertThat(mapper.keyOf(mapper.toMessage(event(6, 5)))).isEqualTo("7");
    }
}
