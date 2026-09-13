package cl.duoc.pedidos360.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import cl.duoc.pedidos360.bff.client.CatalogClient;
import cl.duoc.pedidos360.bff.client.OrdersClient;

/**
 * Clientes declarativos hacia los microservicios de dominio, sobre WebClient no bloqueante.
 * Un WebClient por servicio (URLs base distintas), reutilizado entre peticiones: nada de
 * crear conexiones nuevas por request.
 */
@Configuration
public class WebClientConfig {

    @Bean
    OrdersClient ordersClient(@Value("${pedidos360.clients.orders-base-url}") String baseUrl) {
        return proxyFor(baseUrl, OrdersClient.class);
    }

    @Bean
    CatalogClient catalogClient(@Value("${pedidos360.clients.catalog-base-url}") String baseUrl) {
        return proxyFor(baseUrl, CatalogClient.class);
    }

    private <T> T proxyFor(String baseUrl, Class<T> clientType) {
        WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(webClient))
                .build();
        return factory.createClient(clientType);
    }
}
