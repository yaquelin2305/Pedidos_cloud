package cl.duoc.pedidos360.orders.streaming.producer;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import cl.duoc.pedidos360.orders.entity.OrderStatus;
import cl.duoc.pedidos360.orders.event.OrderEvent;
import cl.duoc.pedidos360.orders.event.OrderEventItem;

class OrderEventMessageMapperTest {
    @ParameterizedTest
    @CsvSource({",CREADO,ORDER_CREATED", "CREADO,ACEPTADO,ORDER_STATUS_CHANGED", "ACEPTADO,CANCELADO,ORDER_CANCELLED"})
    void mapsContractAndKey(OrderStatus previous, OrderStatus status, String eventType) {
        var event = new OrderEvent(UUID.randomUUID(), 42L, "customer", "Cliente", previous, status,
                BigDecimal.TEN, Instant.now(), "operator", "Operador",
                List.of(new OrderEventItem(7L, "Producto", 1, BigDecimal.TEN, BigDecimal.TEN)));
        var mapper = new OrderEventMessageMapper();
        var message = mapper.toMessage(event);
        assertThat(mapper.keyOf(message)).isEqualTo("42");
        assertThat(message.eventType()).isEqualTo(eventType);
        assertThat(message.schemaVersion()).isEqualTo(1);
        assertThat(message.source()).isEqualTo("ms-pedidos360-orders");
        assertThat(message.eventId()).isEqualTo(event.eventId());
        assertThat(message.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(message.actorId()).isEqualTo("operator");
        assertThat(message.actorName()).isEqualTo("Operador");
        assertThat(message.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(7L);
            assertThat(item.subtotal()).isEqualByComparingTo("10");
        });
    }
}
