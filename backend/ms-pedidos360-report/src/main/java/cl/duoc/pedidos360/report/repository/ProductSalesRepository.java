package cl.duoc.pedidos360.report.repository;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import cl.duoc.pedidos360.report.entity.ProductSales;

public interface ProductSalesRepository extends JpaRepository<ProductSales, Long> {

    /** Pedidos de distintas particiones pueden vender el mismo producto: se suma en la BD atomicamente. */
    @Modifying
    @Query(value = """
            insert into report_product_sales (product_id, product_name, units_sold, revenue)
            values (:productId, :productName, :units, :revenue)
            on conflict (product_id) do update set product_name = excluded.product_name,
                units_sold = report_product_sales.units_sold + excluded.units_sold,
                revenue = report_product_sales.revenue + excluded.revenue
            """, nativeQuery = true)
    void addSales(Long productId, String productName, long units, BigDecimal revenue);

    List<ProductSales> findAllByOrderByUnitsSoldDescProductIdAsc(Pageable pageable);
}
