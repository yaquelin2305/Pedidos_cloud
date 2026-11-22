package cl.duoc.pedidos360.report.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cl.duoc.pedidos360.report.entity.OrderSnapshot;
import cl.duoc.pedidos360.report.entity.OrderStatus;
import cl.duoc.pedidos360.report.entity.StockSnapshot;
import cl.duoc.pedidos360.report.repository.OrderSnapshotRepository;
import cl.duoc.pedidos360.report.repository.ProcessedEventRepository;
import cl.duoc.pedidos360.report.repository.ProductSalesRepository;
import cl.duoc.pedidos360.report.repository.StockSnapshotRepository;
import cl.duoc.pedidos360.report.streaming.message.OrderEventMessage;
import cl.duoc.pedidos360.report.streaming.message.OrderLineMessage;
import cl.duoc.pedidos360.report.streaming.message.StockChangedMessage;

/** Proyecciones e idempotencia se confirman juntas; los cambios de un pedido llegan en orden por key. */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReportProjectionService {
    private final ProcessedEventRepository processedEvents;
    private final OrderSnapshotRepository orders;
    private final ProductSalesRepository productSales;
    private final StockSnapshotRepository stocks;

    @Transactional
    public void recordOrderEvent(OrderEventMessage event, EventOrigin origin) {
        if (isDuplicate(event.eventId(), origin)) {
            return;
        }
        OrderSnapshot snapshot = orders.findById(event.orderId())
                .orElseGet(() -> OrderSnapshot.builder().orderId(event.orderId()).build());
        snapshot.setCustomerId(event.customerId());
        snapshot.setStatus(OrderStatus.valueOf(event.status()));
        snapshot.setTotalAmount(event.totalAmount());
        snapshot.setLastEventAt(event.occurredAt());
        if ("ORDER_CREATED".equals(event.eventType())) {
            snapshot.setCreatedAt(event.occurredAt());
        } else if ("ORDER_STATUS_CHANGED".equals(event.eventType())) {
            if ("CREADO".equals(event.previousStatus()) && snapshot.getStatus() == OrderStatus.ACEPTADO) {
                snapshot.setAcceptedAt(event.occurredAt());
                adjustSales(event, 1);
            }
            if (snapshot.getStatus() == OrderStatus.ENTREGADO) {
                snapshot.setDeliveredAt(event.occurredAt());
            } else if ("ENTREGADO".equals(event.previousStatus()) && snapshot.getStatus() == OrderStatus.DESPACHADO) {
                snapshot.setDeliveredAt(null);
            }
        } else if ("ORDER_CANCELLED".equals(event.eventType())) {
            snapshot.setCancelledAt(event.occurredAt());
            if ("ACEPTADO".equals(event.previousStatus())) {
                adjustSales(event, -1);
            }
        }
        orders.save(snapshot);
        log.info("Proyectado {} {} del pedido {}", event.eventType(), event.eventId(), event.orderId());
    }

    @Transactional
    public void recordStockEvent(StockChangedMessage event, EventOrigin origin) {
        if (isDuplicate(event.eventId(), origin)) {
            return;
        }
        stocks.save(StockSnapshot.builder().productId(event.productId()).sku(event.sku()).name(event.name())
                .stock(event.stock()).lowStockThreshold(event.lowStockThreshold()).lowStock(event.lowStock())
                .active(event.active()).updatedAt(event.occurredAt()).build());
        log.info("Proyectado {} {} del producto {}", event.eventType(), event.eventId(), event.productId());
    }

    private boolean isDuplicate(UUID eventId, EventOrigin origin) {
        if (processedEvents.register(eventId, origin.topic(), Instant.now()) == 0) {
            log.info("Evento {} ya procesado, se ignora [topico={} particion={} offset={}]",
                    eventId, origin.topic(), origin.partition(), origin.offset());
            return true;
        }
        return false;
    }

    private void adjustSales(OrderEventMessage event, int direction) {
        // Un orden comun de bloqueo evita deadlocks entre pedidos con varios productos compartidos.
        event.items().stream().sorted(Comparator.comparing(OrderLineMessage::productId)).forEach(item ->
                productSales.addSales(item.productId(), item.productName(), (long) item.quantity() * direction,
                        item.subtotal().multiply(java.math.BigDecimal.valueOf(direction))));
    }
}
