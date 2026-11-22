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
import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Table(name = "report_order_snapshots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSnapshot {
    @Id
    private Long orderId;
    private String customerId;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    @Column(precision = 19, scale = 2)
    private BigDecimal totalAmount;
    private Instant createdAt;
    private Instant acceptedAt;
    private Instant deliveredAt;
    private Instant cancelledAt;
    private Instant lastEventAt;
}
