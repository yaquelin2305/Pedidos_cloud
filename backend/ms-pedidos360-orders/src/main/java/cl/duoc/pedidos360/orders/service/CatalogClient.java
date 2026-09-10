package cl.duoc.pedidos360.orders.service;

/**
 * Integracion sincronica con ms-pedidos360-catalog. Ambos metodos propagan el JWT de la
 * peticion original (ver CatalogClientImpl) para que catalog aplique la misma validacion de
 * seguridad que si el cliente lo llamara directamente.
 */
public interface CatalogClient {

    CatalogProductDTO getProduct(Long productId);

    void decreaseStock(Long productId, int quantity);
}
