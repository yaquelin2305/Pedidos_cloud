package cl.duoc.pedidos360.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import cl.duoc.pedidos360.report.entity.OrderSnapshot;

public interface OrderSnapshotRepository extends JpaRepository<OrderSnapshot, Long> {
}
