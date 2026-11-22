package cl.duoc.pedidos360.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import cl.duoc.pedidos360.audit.entity.AggregateType;
import cl.duoc.pedidos360.audit.entity.AuditEntry;
import cl.duoc.pedidos360.audit.repository.AuditEntryRepository;
import cl.duoc.pedidos360.audit.streaming.message.OrderEventMessage;
import cl.duoc.pedidos360.audit.streaming.message.OrderLineMessage;
import cl.duoc.pedidos360.audit.streaming.message.StockChangedMessage;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final EventOrigin ORIGIN = new EventOrigin("pedidos360.orders.events", 2, 41L);
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-21T12:00:00Z");

    @Mock
    private AuditEntryRepository repository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        // Igual que el ObjectMapper de Spring Boot: fechas en ISO-8601.
        auditService = new AuditService(repository, JsonMapper.builder().findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
    }

    private OrderEventMessage orderEvent(String previousStatus, String status) {
        return new OrderEventMessage(UUID.randomUUID(), "ORDER_STATUS_CHANGED", 1, "ms-pedidos360-orders",
                OCCURRED_AT, 12L, "cust-1", "Cliente", "user-9", "Ana", previousStatus, status,
                BigDecimal.valueOf(100),
                List.of(new OrderLineMessage(3L, "Teclado", 2, BigDecimal.valueOf(50), BigDecimal.valueOf(100))));
    }

    private StockChangedMessage stockEvent(String eventType, Integer previousStock, int stock) {
        return new StockChangedMessage(UUID.randomUUID(), eventType, 1, "ms-pedidos360-catalog", OCCURRED_AT,
                7L, "SKU-7", "Mouse", previousStock, stock, 5, stock <= 5, true);
    }

    private AuditEntry savedEntry() {
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository).save(entry.capture());
        return entry.getValue();
    }

    @Test
    void registraElEventoDePedidoConSuResumenYOrigen() {
        OrderEventMessage event = orderEvent("ACEPTADO", "EN_PREPARACION");

        auditService.recordOrderEvent(event, ORIGIN);

        AuditEntry entry = savedEntry();
        assertThat(entry.getEventId()).isEqualTo(event.eventId());
        assertThat(entry.getAggregateType()).isEqualTo(AggregateType.ORDER);
        assertThat(entry.getAggregateId()).isEqualTo(12L);
        assertThat(entry.getActorId()).isEqualTo("user-9");
        assertThat(entry.getActorName()).isEqualTo("Ana");
        assertThat(entry.getSummary()).isEqualTo("Pedido 12: ACEPTADO → EN_PREPARACION por Ana");
        assertThat(entry.getTopic()).isEqualTo("pedidos360.orders.events");
        assertThat(entry.getKafkaPartition()).isEqualTo(2);
        assertThat(entry.getKafkaOffset()).isEqualTo(41L);
        assertThat(entry.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(entry.getReceivedAt()).isNotNull();
        assertThat(entry.getPayload()).contains("\"orderId\":12").contains("\"occurredAt\":\"2026-11-21T12:00:00Z\"");
    }

    @Test
    void resumeLaCreacionDelPedido() {
        auditService.recordOrderEvent(orderEvent(null, "CREADO"), ORIGIN);

        assertThat(savedEntry().getSummary()).isEqualTo("Pedido 12 creado en CREADO por Ana");
    }

    @Test
    void registraElCambioDeStockSinActor() {
        StockChangedMessage event = stockEvent("STOCK_DECREASED", 10, 6);

        auditService.recordStockEvent(event, new EventOrigin("pedidos360.catalog.stock", 1, 5L));

        AuditEntry entry = savedEntry();
        assertThat(entry.getAggregateType()).isEqualTo(AggregateType.PRODUCT);
        assertThat(entry.getAggregateId()).isEqualTo(7L);
        assertThat(entry.getEventType()).isEqualTo("STOCK_DECREASED");
        assertThat(entry.getActorId()).isNull();
        assertThat(entry.getSummary()).isEqualTo("Producto 7 (SKU-7): stock 10 → 6 (STOCK_DECREASED)");
    }

    @Test
    void resumeLaCreacionYLaBajaDelProducto() {
        auditService.recordStockEvent(stockEvent("PRODUCT_CREATED", null, 20), ORIGIN);
        assertThat(savedEntry().getSummary()).isEqualTo("Producto 7 (SKU-7) creado con stock 20");
    }

    @Test
    void ignoraUnEventoDePedidoYaAuditado() {
        OrderEventMessage event = orderEvent("CREADO", "ACEPTADO");
        when(repository.existsByEventId(event.eventId())).thenReturn(true);

        auditService.recordOrderEvent(event, ORIGIN);

        verify(repository, never()).save(any());
    }

    @Test
    void ignoraUnCambioDeStockYaAuditado() {
        StockChangedMessage event = stockEvent("STOCK_ADJUSTED", 1, 2);
        when(repository.existsByEventId(event.eventId())).thenReturn(true);

        auditService.recordStockEvent(event, ORIGIN);

        verify(repository, never()).save(any());
    }
}
