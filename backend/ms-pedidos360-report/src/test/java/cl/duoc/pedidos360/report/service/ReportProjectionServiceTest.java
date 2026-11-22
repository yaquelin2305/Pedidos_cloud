package cl.duoc.pedidos360.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cl.duoc.pedidos360.report.entity.OrderSnapshot;
import cl.duoc.pedidos360.report.entity.OrderStatus;
import cl.duoc.pedidos360.report.entity.StockSnapshot;
import cl.duoc.pedidos360.report.repository.*;
import cl.duoc.pedidos360.report.streaming.message.*;

@ExtendWith(MockitoExtension.class)
class ReportProjectionServiceTest {
    @Mock private ProcessedEventRepository processedEvents;
    @Mock private OrderSnapshotRepository orders;
    @Mock private ProductSalesRepository sales;
    @Mock private StockSnapshotRepository stocks;
    private ReportProjectionService service;
    private final EventOrigin origin = new EventOrigin("orders", 0, 1);
    private final Instant occurredAt = Instant.parse("2026-01-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        service = new ReportProjectionService(processedEvents, orders, sales, stocks);
    }

    private OrderEventMessage event(String type, String previous, String status) {
        return new OrderEventMessage(UUID.randomUUID(), type, 1, "ms-pedidos360-orders", occurredAt, 1L,
                "customer", "Cliente", "operator", "Operador", previous, status, new BigDecimal("100"),
                List.of(new OrderLineMessage(7L, "Producto", 2, new BigDecimal("50"), new BigDecimal("100"))));
    }

    private void allowEvent() {
        when(processedEvents.register(any(), anyString(), any())).thenReturn(1);
    }

    private OrderSnapshot capturedOrder() {
        var captured = ArgumentCaptor.forClass(OrderSnapshot.class);
        verify(orders).save(captured.capture());
        return captured.getValue();
    }

    @Test
    void createsSnapshot() {
        allowEvent();
        service.recordOrderEvent(event("ORDER_CREATED", null, "CREADO"), origin);
        var order = capturedOrder();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREADO);
        assertThat(order.getCreatedAt()).isEqualTo(occurredAt);
        assertThat(order.getAcceptedAt()).isNull();
        verifyNoInteractions(sales);
    }

    @Test
    void acceptanceAddsSalesAndSetsAcceptanceTime() {
        allowEvent();
        when(orders.findById(1L)).thenReturn(Optional.of(OrderSnapshot.builder().orderId(1L)
                .createdAt(occurredAt.minusSeconds(60)).build()));
        service.recordOrderEvent(event("ORDER_STATUS_CHANGED", "CREADO", "ACEPTADO"), origin);
        assertThat(capturedOrder().getAcceptedAt()).isEqualTo(occurredAt);
        verify(sales).addSales(7L, "Producto", 2L, new BigDecimal("100"));
    }

    @Test
    void deliveryCorrectionClearsDeliveryWithoutAddingSales() {
        allowEvent();
        when(orders.findById(1L)).thenReturn(Optional.of(OrderSnapshot.builder().orderId(1L)
                .acceptedAt(occurredAt.minusSeconds(120)).deliveredAt(occurredAt.minusSeconds(60)).build()));
        service.recordOrderEvent(event("ORDER_STATUS_CHANGED", "ENTREGADO", "DESPACHADO"), origin);
        var order = capturedOrder();
        assertThat(order.getDeliveredAt()).isNull();
        assertThat(order.getAcceptedAt()).isEqualTo(occurredAt.minusSeconds(120));
        verifyNoInteractions(sales);
    }

    @Test
    void deliverySetsTimeEvenWithoutExistingSnapshot() {
        allowEvent();
        service.recordOrderEvent(event("ORDER_STATUS_CHANGED", "DESPACHADO", "ENTREGADO"), origin);
        var order = capturedOrder();
        assertThat(order.getDeliveredAt()).isEqualTo(occurredAt);
        assertThat(order.getCreatedAt()).isNull();
        assertThat(order.getTotalAmount()).isEqualByComparingTo("100");
    }

    @Test
    void cancellationFromAcceptedSubtractsSales() {
        allowEvent();
        service.recordOrderEvent(event("ORDER_CANCELLED", "ACEPTADO", "CANCELADO"), origin);
        assertThat(capturedOrder().getCancelledAt()).isEqualTo(occurredAt);
        verify(sales).addSales(7L, "Producto", -2L, new BigDecimal("-100"));
    }

    @Test
    void cancellationFromCreatedDoesNotSubtractSales() {
        allowEvent();
        service.recordOrderEvent(event("ORDER_CANCELLED", "CREADO", "CANCELADO"), origin);
        assertThat(capturedOrder().getStatus()).isEqualTo(OrderStatus.CANCELADO);
        verifyNoInteractions(sales);
    }

    @Test
    void ignoresDuplicateWithoutChangingProjections() {
        service.recordOrderEvent(event("ORDER_STATUS_CHANGED", "CREADO", "ACEPTADO"), origin);
        verifyNoInteractions(orders, sales, stocks);
    }

    @Test
    void stockUpdatesSnapshotAndIgnoresDuplicate() {
        var event = new StockChangedMessage(UUID.randomUUID(), "STOCK_ADJUSTED", 1, "ms-pedidos360-catalog",
                occurredAt, 7L, "SKU", "Producto", 10, 2, 5, true, true);
        when(processedEvents.register(eq(event.eventId()), anyString(), any())).thenReturn(1, 0);
        service.recordStockEvent(event, origin);
        service.recordStockEvent(event, origin);
        var captured = ArgumentCaptor.forClass(StockSnapshot.class);
        verify(stocks).save(captured.capture());
        assertThat(captured.getValue().getStock()).isEqualTo(2);
        assertThat(captured.getValue().isLowStock()).isTrue();
    }
}
