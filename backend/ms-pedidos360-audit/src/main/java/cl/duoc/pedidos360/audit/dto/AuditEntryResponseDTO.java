package cl.duoc.pedidos360.audit.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonRawValue;

import cl.duoc.pedidos360.audit.entity.AggregateType;
import cl.duoc.pedidos360.audit.entity.AuditEntry;

/**
 * Entrada de la bitacora en la API. {@code payload} se devuelve como objeto JSON, no como texto.
 */
public record AuditEntryResponseDTO(
        Long id,
        UUID eventId,
        String topic,
        int partition,
        long offset,
        String eventType,
        AggregateType aggregateType,
        Long aggregateId,
        String actorId,
        String actorName,
        String summary,
        @JsonRawValue String payload,
        Instant occurredAt,
        Instant receivedAt) {

    public static AuditEntryResponseDTO from(AuditEntry entry) {
        return new AuditEntryResponseDTO(entry.getId(), entry.getEventId(), entry.getTopic(),
                entry.getKafkaPartition(), entry.getKafkaOffset(), entry.getEventType(), entry.getAggregateType(),
                entry.getAggregateId(), entry.getActorId(), entry.getActorName(), entry.getSummary(),
                entry.getPayload(), entry.getOccurredAt(), entry.getReceivedAt());
    }
}
