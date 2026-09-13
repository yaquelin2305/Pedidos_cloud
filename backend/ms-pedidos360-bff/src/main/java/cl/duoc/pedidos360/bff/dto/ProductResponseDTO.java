package cl.duoc.pedidos360.bff.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponseDTO(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String category,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
