package cl.duoc.pedidos360.orders.service;

import java.math.BigDecimal;

/**
 * Proyeccion minima de la respuesta de GET /api/catalog/products/{id}. No es un DTO de la API
 * de orders: solo modela lo que este microservicio necesita leer de catalog.
 */
public record CatalogProductDTO(Long id, String name, BigDecimal price, Integer stock, boolean active) {
}
