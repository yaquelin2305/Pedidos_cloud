package cl.duoc.pedidos360.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reglas de negocio configurables del catalogo.
 *
 * @param lowStockThreshold stock a partir del cual un producto se considera bajo y se emite una alerta
 */
@ConfigurationProperties("pedidos360.catalog")
public record CatalogProperties(int lowStockThreshold) {
}
