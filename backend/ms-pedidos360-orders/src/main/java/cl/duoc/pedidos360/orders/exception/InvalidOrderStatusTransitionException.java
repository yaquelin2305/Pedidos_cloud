package cl.duoc.pedidos360.orders.exception;

import cl.duoc.pedidos360.orders.entity.OrderStatus;

public class InvalidOrderStatusTransitionException extends RuntimeException {

    public InvalidOrderStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("No se puede pasar el pedido de " + from + " a " + to);
    }
}
