package cl.duoc.pedidos360.orders.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("Pedido no encontrado: " + orderId);
    }
}
