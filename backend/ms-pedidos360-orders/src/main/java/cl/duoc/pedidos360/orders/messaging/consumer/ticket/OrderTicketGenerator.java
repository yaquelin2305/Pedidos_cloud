package cl.duoc.pedidos360.orders.messaging.consumer.ticket;

import org.springframework.stereotype.Component;

import cl.duoc.pedidos360.orders.event.OrderEvent;
import lombok.extern.slf4j.Slf4j;

/** El comprobante es texto en el log; no se persiste ni genera un archivo. */
@Slf4j
@Component
public class OrderTicketGenerator {
    public void generate(OrderEvent event) {
        log.info("Comprobante: pedido #{} | cliente: {} ({}) | total: {} | fecha: {}",
                event.orderId(), event.customerName(), event.customerId(), event.totalAmount(), event.occurredAt());
    }
}
