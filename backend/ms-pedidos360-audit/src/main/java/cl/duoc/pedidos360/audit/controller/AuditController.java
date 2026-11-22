package cl.duoc.pedidos360.audit.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.audit.dto.AuditEntryResponseDTO;
import cl.duoc.pedidos360.audit.entity.AggregateType;
import cl.duoc.pedidos360.audit.service.AuditService;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Admin')")
public class AuditController {

    private final AuditService auditService;

    /** Bitacora paginada, del evento mas reciente al mas antiguo, con filtros opcionales por agregado. */
    @GetMapping("/entries")
    public ResponseEntity<Page<AuditEntryResponseDTO>> entries(
            @RequestParam(required = false) AggregateType aggregateType,
            @RequestParam(required = false) Long aggregateId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(auditService.search(aggregateType, aggregateId, page, size));
    }
}
