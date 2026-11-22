package cl.duoc.pedidos360.audit.repository;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import cl.duoc.pedidos360.audit.entity.AggregateType;
import cl.duoc.pedidos360.audit.entity.AuditEntry;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long>, JpaSpecificationExecutor<AuditEntry> {

    boolean existsByEventId(UUID eventId);

    /** Filtros opcionales de la consulta: un valor {@code null} no restringe. */
    static Specification<AuditEntry> matching(AggregateType aggregateType, Long aggregateId) {
        return (root, query, cb) -> cb.and(
                aggregateType == null ? cb.conjunction() : cb.equal(root.get("aggregateType"), aggregateType),
                aggregateId == null ? cb.conjunction() : cb.equal(root.get("aggregateId"), aggregateId));
    }
}
