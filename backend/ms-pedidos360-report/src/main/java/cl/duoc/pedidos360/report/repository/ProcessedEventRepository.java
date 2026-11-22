package cl.duoc.pedidos360.report.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import cl.duoc.pedidos360.report.entity.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /** La reserva y la proyeccion comparten transaccion: ante un fallo ambas se revierten. */
    @Modifying
    @Query(value = """
            insert into report_processed_events (event_id, topic, processed_at)
            values (:eventId, :topic, :processedAt) on conflict (event_id) do nothing
            """, nativeQuery = true)
    int register(UUID eventId, String topic, Instant processedAt);
}
