package cl.duoc.pedidos360.report.dto;

import java.math.BigDecimal;
import cl.duoc.pedidos360.report.entity.ProductSales;

public record ProductSalesDTO(Long productId, String productName, long unitsSold, BigDecimal revenue) {
    public static ProductSalesDTO from(ProductSales sales) {
        return new ProductSalesDTO(sales.getProductId(), sales.getProductName(), sales.getUnitsSold(), sales.getRevenue());
    }
}
