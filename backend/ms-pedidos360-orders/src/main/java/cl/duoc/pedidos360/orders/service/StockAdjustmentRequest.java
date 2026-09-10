package cl.duoc.pedidos360.orders.service;

/**
 * Cuerpo saliente hacia PATCH /api/catalog/products/{id}/stock/decrease. Refleja el
 * StockAdjustmentDTO de ms-pedidos360-catalog.
 */
public record StockAdjustmentRequest(Long productId, Integer quantity) {
}
