package cl.duoc.pedidos360.orders.service;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.orders.exception.StockUnavailableException;

/**
 * Cliente HTTP sincronico hacia catalog, con RestClient (Spring 6): esta app es un Spring MVC
 * clasico (no reactivo), asi que RestClient evita sumar la dependencia de WebFlux y el uso de
 * Mono/block() que un WebClient exigiria aqui sin aportar nada, ya que todo el flujo es bloqueante
 * de por si (el hilo de la peticion original queda esperando la respuesta de catalog).
 */
@Component
@RequiredArgsConstructor
public class CatalogClientImpl implements CatalogClient {

    private final RestClient catalogRestClient;

    @Override
    public CatalogProductDTO getProduct(Long productId) {
        try {
            return catalogRestClient.get()
                    .uri("/api/catalog/products/{id}", productId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .retrieve()
                    .body(CatalogProductDTO.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new StockUnavailableException(productId, "producto inexistente en el catalogo");
        }
    }

    /**
     * Descuenta stock al aceptar un pedido. catalog responde 409 si no hay stock suficiente y
     * 404 si el producto no existe; ambos casos se traducen a StockUnavailableException para que
     * OrderServiceImpl aborte la transicion a ACEPTADO. Ver limitacion de compensacion en
     * OrderServiceImpl.acceptOrder: si esta linea falla despues de que otra ya descarto stock,
     * ese descuento previo no se revierte en esta etapa.
     */
    @Override
    public void decreaseStock(Long productId, int quantity) {
        try {
            catalogRestClient.patch()
                    .uri("/api/catalog/products/{id}/stock/decrease", productId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .body(new StockAdjustmentRequest(productId, quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict ex) {
            throw new StockUnavailableException(productId, "stock insuficiente");
        } catch (HttpClientErrorException.NotFound ex) {
            throw new StockUnavailableException(productId, "producto inexistente en el catalogo");
        }
    }

    private String bearerToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return "Bearer " + jwtAuth.getToken().getTokenValue();
        }
        throw new IllegalStateException("No hay un JWT en el contexto de seguridad para propagar a catalog");
    }
}
