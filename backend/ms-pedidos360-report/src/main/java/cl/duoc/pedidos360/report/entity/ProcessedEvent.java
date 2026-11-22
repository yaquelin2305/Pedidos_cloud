package cl.duoc.pedidos360.report.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "report_processed_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {
    @Id
    private UUID eventId;
    private String topic;
    private Instant processedAt;
}
