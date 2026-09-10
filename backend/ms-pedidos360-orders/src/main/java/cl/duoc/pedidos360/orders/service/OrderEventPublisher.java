package cl.duoc.pedidos360.orders.service;

import cl.duoc.pedidos360.orders.entity.Order;
import cl.duoc.pedidos360.orders.entity.OrderStatus;

/**
 * Punto de extension para publicar el evento de cambio de estado del pedido cuando la
 * infraestructura de mensajeria este disponible: topic Kafka "orders.events" y cola RabbitMQ
 * "q.cmd.email" para notificaciones. Fuera de alcance en esta etapa (ver README): solo se deja
 * la interfaz para no bloquear el resto del desarrollo ni acoplar el Service a un broker.
 */
public interface OrderEventPublisher {

    void publishStatusChanged(Order order, OrderStatus previousStatus);
}
