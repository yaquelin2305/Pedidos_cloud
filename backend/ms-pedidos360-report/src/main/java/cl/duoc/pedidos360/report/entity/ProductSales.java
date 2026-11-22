package cl.duoc.pedidos360.report.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import jakarta.persistence.Column;

@Entity
@Table(name = "report_product_sales")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSales {
    @Id
    private Long productId;
    private String productName;
    private Long unitsSold;
    @Column(precision = 19, scale = 2)
    private BigDecimal revenue;
}
