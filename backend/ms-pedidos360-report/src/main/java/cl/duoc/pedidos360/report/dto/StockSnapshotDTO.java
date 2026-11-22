package cl.duoc.pedidos360.report.dto;

import java.time.Instant;
import cl.duoc.pedidos360.report.entity.StockSnapshot;

public record StockSnapshotDTO(Long productId, String sku, String name, Integer stock,
                               Integer lowStockThreshold, boolean lowStock, boolean active, Instant updatedAt) {
    public static StockSnapshotDTO from(StockSnapshot stock) {
        return new StockSnapshotDTO(stock.getProductId(), stock.getSku(), stock.getName(), stock.getStock(),
                stock.getLowStockThreshold(), stock.isLowStock(), stock.isActive(), stock.getUpdatedAt());
    }
}
