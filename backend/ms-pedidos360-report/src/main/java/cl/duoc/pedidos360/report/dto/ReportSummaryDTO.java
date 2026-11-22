package cl.duoc.pedidos360.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import cl.duoc.pedidos360.report.entity.OrderStatus;

public record ReportSummaryDTO(Map<OrderStatus, Long> ordersByStatus, BigDecimal confirmedSales,
                               long deliveredOrders, long cancelledOrders, Double averageLeadTimeMinutes,
                               Instant lastEventAt) {
}
