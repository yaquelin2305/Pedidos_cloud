package cl.duoc.pedidos360.orders.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import cl.duoc.pedidos360.orders.entity.Order;
import cl.duoc.pedidos360.orders.entity.OrderStatus;

/**
 * Implementacion no-op (solo deja registro) de OrderEventPublisher. Se reemplaza por un
 * productor real de Kafka/RabbitMQ cuando esa infraestructura se integre en un parcial
 * posterior; mientras tanto evita que OrderServiceImpl dependa de un broker que no existe.
 */
@Component
public class LoggingOrderEventPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingOrderEventPublisher.class);

    @Override
    public void publishStatusChanged(Order order, OrderStatus previousStatus) {
        // TODO: publicar a Kafka (topic "orders.events") y encolar notificacion en RabbitMQ
        // (cola "q.cmd.email") cuando la infraestructura de broker este levantada.
        log.info("Evento de cambio de estado: pedido {} paso de {} a {}",
                order.getId(), previousStatus, order.getStatus());
    }
}
