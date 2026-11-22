package cl.duoc.pedidos360.report.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import cl.duoc.pedidos360.report.entity.StockSnapshot;

public interface StockSnapshotRepository extends JpaRepository<StockSnapshot, Long> {
    List<StockSnapshot> findByActiveTrueAndLowStockTrueOrderByProductIdAsc();
}
