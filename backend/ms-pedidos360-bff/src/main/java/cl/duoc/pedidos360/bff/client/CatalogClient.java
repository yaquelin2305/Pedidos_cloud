package cl.duoc.pedidos360.bff.client;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import cl.duoc.pedidos360.bff.dto.CatalogPageDTO;
import cl.duoc.pedidos360.bff.dto.ProductRequestDTO;
import cl.duoc.pedidos360.bff.dto.ProductResponseDTO;
import cl.duoc.pedidos360.bff.dto.StockUpdateDTO;

/**
 * Espejo declarativo de /api/catalog/products en ms-pedidos360-catalog.
 */
public interface CatalogClient {

    @GetExchange("/api/catalog/products")
    CatalogPageDTO list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @GetExchange("/api/catalog/products/{id}")
    ProductResponseDTO getById(@PathVariable Long id, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PostExchange("/api/catalog/products")
    ProductResponseDTO create(@RequestBody ProductRequestDTO request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PutExchange("/api/catalog/products/{id}")
    ProductResponseDTO update(@PathVariable Long id, @RequestBody ProductRequestDTO request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @DeleteExchange("/api/catalog/products/{id}")
    void deactivate(@PathVariable Long id, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PatchExchange("/api/catalog/products/{id}/stock")
    ProductResponseDTO updateStock(@PathVariable Long id, @RequestBody StockUpdateDTO update,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
}
