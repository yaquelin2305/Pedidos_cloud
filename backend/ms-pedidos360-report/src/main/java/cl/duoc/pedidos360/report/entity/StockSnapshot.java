package cl.duoc.pedidos360.report.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "report_stock_snapshots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSnapshot {
    @Id
    private Long productId;
    private String sku;
    private String name;
    private Integer stock;
    private Integer lowStockThreshold;
    private boolean lowStock;
    private boolean active;
    private Instant updatedAt;
}
