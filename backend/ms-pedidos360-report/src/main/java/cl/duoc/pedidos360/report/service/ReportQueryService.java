package cl.duoc.pedidos360.report.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import cl.duoc.pedidos360.report.dto.ProductSalesDTO;
import cl.duoc.pedidos360.report.dto.ReportSummaryDTO;
import cl.duoc.pedidos360.report.dto.StockSnapshotDTO;
import cl.duoc.pedidos360.report.entity.OrderSnapshot;
import cl.duoc.pedidos360.report.entity.OrderStatus;
import cl.duoc.pedidos360.report.repository.OrderSnapshotRepository;
import cl.duoc.pedidos360.report.repository.ProductSalesRepository;
import cl.duoc.pedidos360.report.repository.StockSnapshotRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportQueryService {
    private final OrderSnapshotRepository orders;
    private final ProductSalesRepository productSales;
    private final StockSnapshotRepository stocks;

    public ReportSummaryDTO summary() {
        List<OrderSnapshot> snapshots = orders.findAll();
        var counts = new EnumMap<OrderStatus, Long>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, 0L);
        }
        snapshots.forEach(order -> counts.merge(order.getStatus(), 1L, Long::sum));
        BigDecimal sales = snapshots.stream()
                .filter(order -> order.getAcceptedAt() != null && order.getStatus() != OrderStatus.CANCELADO)
                .map(OrderSnapshot::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Sin el evento de creacion no se inventa una fecha ni un lead time.
        var leadTime = snapshots.stream().filter(order -> order.getStatus() == OrderStatus.ENTREGADO
                        && order.getCreatedAt() != null && order.getDeliveredAt() != null)
                .mapToDouble(order -> Duration.between(order.getCreatedAt(), order.getDeliveredAt()).toMillis() / 60000.0)
                .average();
        Instant lastEvent = Stream.concat(snapshots.stream().map(OrderSnapshot::getLastEventAt),
                        stocks.findAll().stream().map(stock -> stock.getUpdatedAt()))
                .filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
        return new ReportSummaryDTO(counts, sales, counts.get(OrderStatus.ENTREGADO), counts.get(OrderStatus.CANCELADO),
                leadTime.isPresent() ? leadTime.getAsDouble() : null, lastEvent);
    }

    public List<ProductSalesDTO> topProducts(int limit) {
        return productSales.findAllByOrderByUnitsSoldDescProductIdAsc(PageRequest.of(0, limit)).stream()
                .map(ProductSalesDTO::from).toList();
    }

    public List<StockSnapshotDTO> lowStock() {
        return stocks.findByActiveTrueAndLowStockTrueOrderByProductIdAsc().stream().map(StockSnapshotDTO::from).toList();
    }
}
