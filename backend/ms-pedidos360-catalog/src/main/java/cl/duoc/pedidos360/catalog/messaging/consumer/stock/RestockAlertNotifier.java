package cl.duoc.pedidos360.catalog.messaging.consumer.stock;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import cl.duoc.pedidos360.catalog.event.StockLowEvent;

/** Registra la alerta de reposicion de un producto que quedo con stock bajo. */
@Slf4j
@Component
public class RestockAlertNotifier {

    public void notifyRestock(StockLowEvent event) {
        log.info("Alerta de reposicion: producto {} '{}' (SKU {}) quedo con stock {}, umbral {}",
                event.productId(), event.name(), event.sku(), event.stock(), event.threshold());
    }
}
