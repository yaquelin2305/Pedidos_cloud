package cl.duoc.pedidos360.orders.messaging.consumer.notification;

import org.springframework.stereotype.Component;

import cl.duoc.pedidos360.orders.event.OrderEvent;
import lombok.extern.slf4j.Slf4j;

/** La notificacion de esta etapa se registra en logs, sin canales externos. */
@Slf4j
@Component
public class OrderNotificationNotifier {
    public void notifyCustomer(OrderEvent event) {
        log.info("Notificacion: pedido #{} de {} paso de {} a {}",
                event.orderId(), event.customerName(), event.previousStatus(), event.status());
    }
}
