package cl.duoc.pedidos360.audit.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import cl.duoc.pedidos360.audit.dto.AuditEntryResponseDTO;
import cl.duoc.pedidos360.audit.entity.AggregateType;
import cl.duoc.pedidos360.audit.entity.AuditEntry;
import cl.duoc.pedidos360.audit.repository.AuditEntryRepository;
import cl.duoc.pedidos360.audit.streaming.message.OrderEventMessage;
import cl.duoc.pedidos360.audit.streaming.message.StockChangedMessage;

/**
 * Registra cada evento de negocio en la bitacora y la consulta. No conoce Kafka: recibe el evento ya
 * validado y su posicion de origen.
 *
 * <p>Idempotente por {@code eventId}: un evento ya registrado (re-entrega o reintento) se ignora. Los
 * eventos de un mismo agregado llegan siempre por la misma particion y cada particion la procesa un solo
 * consumidor del grupo, asi que dos copias del mismo evento nunca se procesan a la vez.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final String UNKNOWN_ACTOR = "desconocido";

    private final AuditEntryRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordOrderEvent(OrderEventMessage event, EventOrigin origin) {
        if (isDuplicate(event.eventId(), origin)) {
            return;
        }
        repository.save(AuditEntry.builder()
                .eventId(event.eventId())
                .topic(origin.topic())
                .kafkaPartition(origin.partition())
                .kafkaOffset(origin.offset())
                .eventType(event.eventType())
                .aggregateType(AggregateType.ORDER)
                .aggregateId(event.orderId())
                .actorId(event.actorId())
                .actorName(event.actorName())
                .summary(orderSummary(event))
                .payload(toJson(event))
                .occurredAt(event.occurredAt())
                .receivedAt(Instant.now())
                .build());
        log.info("Auditado {} {} del pedido {}", event.eventType(), event.eventId(), event.orderId());
    }

    @Transactional
    public void recordStockEvent(StockChangedMessage event, EventOrigin origin) {
        if (isDuplicate(event.eventId(), origin)) {
            return;
        }
        repository.save(AuditEntry.builder()
                .eventId(event.eventId())
                .topic(origin.topic())
                .kafkaPartition(origin.partition())
                .kafkaOffset(origin.offset())
                .eventType(event.eventType())
                .aggregateType(AggregateType.PRODUCT)
                .aggregateId(event.productId())
                .summary(stockSummary(event))
                .payload(toJson(event))
                .occurredAt(event.occurredAt())
                .receivedAt(Instant.now())
                .build());
        log.info("Auditado {} {} del producto {}", event.eventType(), event.eventId(), event.productId());
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponseDTO> search(AggregateType aggregateType, Long aggregateId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return repository.findAll(AuditEntryRepository.matching(aggregateType, aggregateId), pageRequest)
                .map(AuditEntryResponseDTO::from);
    }

    private boolean isDuplicate(UUID eventId, EventOrigin origin) {
        if (repository.existsByEventId(eventId)) {
            log.info("Evento {} ya auditado, se ignora la re-entrega [topico={} particion={} offset={}]",
                    eventId, origin.topic(), origin.partition(), origin.offset());
            return true;
        }
        return false;
    }

    private String orderSummary(OrderEventMessage event) {
        String actor = event.actorName() != null ? event.actorName() : UNKNOWN_ACTOR;
        if (event.previousStatus() == null) {
            return "Pedido %d creado en %s por %s".formatted(event.orderId(), event.status(), actor);
        }
        return "Pedido %d: %s → %s por %s".formatted(event.orderId(), event.previousStatus(), event.status(), actor);
    }

    private String stockSummary(StockChangedMessage event) {
        String product = "Producto %d (%s)".formatted(event.productId(), event.sku());
        return switch (event.eventType()) {
            case "PRODUCT_CREATED" -> "%s creado con stock %d".formatted(product, event.stock());
            case "PRODUCT_DEACTIVATED" -> "%s desactivado".formatted(product);
            default -> "%s: stock %s → %d (%s)".formatted(product, event.previousStock(), event.stock(),
                    event.eventType());
        };
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el evento para la bitacora", ex);
        }
    }
}
