package cl.duoc.pedidos360.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import cl.duoc.pedidos360.report.entity.*;
import cl.duoc.pedidos360.report.repository.*;

class ReportQueryServiceTest {
    private final OrderSnapshotRepository orders = mock(OrderSnapshotRepository.class);
    private final ProductSalesRepository sales = mock(ProductSalesRepository.class);
    private final StockSnapshotRepository stocks = mock(StockSnapshotRepository.class);
    private final ReportQueryService service = new ReportQueryService(orders, sales, stocks);

    @Test
    void summaryIncludesZeroCountsAndNullLeadTimeWhenEmpty() {
        var summary = service.summary();
        assertThat(summary.ordersByStatus()).hasSize(6).allSatisfy((status, count) -> assertThat(count).isZero());
        assertThat(summary.confirmedSales()).isEqualByComparingTo("0");
        assertThat(summary.averageLeadTimeMinutes()).isNull();
        assertThat(summary.lastEventAt()).isNull();
    }

    @Test
    void summaryExcludesCancelledSalesAndComputesAverageLeadTime() {
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        when(orders.findAll()).thenReturn(List.of(
                OrderSnapshot.builder().status(OrderStatus.ENTREGADO).totalAmount(BigDecimal.TEN)
                    .createdAt(start).acceptedAt(start).deliveredAt(start.plusSeconds(90))
                    .lastEventAt(start.plusSeconds(90)).build(),
                OrderSnapshot.builder().status(OrderStatus.ENTREGADO).totalAmount(BigDecimal.TEN)
                    .createdAt(start).acceptedAt(start).deliveredAt(start.plusSeconds(270)).build(),
                OrderSnapshot.builder().status(OrderStatus.CANCELADO).totalAmount(BigDecimal.TEN)
                    .acceptedAt(start).build(),
                OrderSnapshot.builder().status(OrderStatus.CREADO).totalAmount(BigDecimal.TEN).build()));
        when(stocks.findAll()).thenReturn(List.of(StockSnapshot.builder().updatedAt(start.plusSeconds(300)).build()));
        var summary = service.summary();
        assertThat(summary.confirmedSales()).isEqualByComparingTo("20");
        assertThat(summary.deliveredOrders()).isEqualTo(2);
        assertThat(summary.cancelledOrders()).isEqualTo(1);
        assertThat(summary.averageLeadTimeMinutes()).isEqualTo(3.0);
        assertThat(summary.ordersByStatus().get(OrderStatus.ACEPTADO)).isZero();
        assertThat(summary.lastEventAt()).isEqualTo(start.plusSeconds(300));
    }
    @Test
    void delegatesRankingLimitAndMapsProductValues() {
        var page = org.springframework.data.domain.PageRequest.of(0, 3);
        when(sales.findAllByOrderByUnitsSoldDescProductIdAsc(page)).thenReturn(List.of(
                ProductSales.builder().productId(1L).productName("Producto").unitsSold(4L)
                        .revenue(new BigDecimal("80")).build()));
        assertThat(service.topProducts(3)).singleElement().satisfies(product -> {
            assertThat(product.unitsSold()).isEqualTo(4L);
            assertThat(product.revenue()).isEqualByComparingTo("80");
        });
        verify(sales).findAllByOrderByUnitsSoldDescProductIdAsc(page);
    }

    @Test
    void queriesOnlyActiveLowStockAndMapsSnapshot() {
        when(stocks.findByActiveTrueAndLowStockTrueOrderByProductIdAsc()).thenReturn(List.of(
                StockSnapshot.builder().productId(2L).sku("SKU").name("Producto").stock(2)
                        .lowStockThreshold(5).lowStock(true).active(true).build()));
        assertThat(service.lowStock()).singleElement().satisfies(stock -> {
            assertThat(stock.productId()).isEqualTo(2L);
            assertThat(stock.stock()).isEqualTo(2);
            assertThat(stock.active()).isTrue();
        });
    }
}
