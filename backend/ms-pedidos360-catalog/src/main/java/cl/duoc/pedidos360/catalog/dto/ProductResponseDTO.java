package cl.duoc.pedidos360.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;

import cl.duoc.pedidos360.catalog.entity.Product;

/**
 * Contrato de salida. La entidad JPA nunca se serializa directamente: este DTO
 * evita exponer el campo de bloqueo optimista (version) al cliente final.
 */
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

    public static ProductResponseDTO from(Product product) {
        return new ProductResponseDTO(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getCategory(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
