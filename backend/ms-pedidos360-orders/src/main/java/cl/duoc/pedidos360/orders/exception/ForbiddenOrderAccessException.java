package cl.duoc.pedidos360.orders.exception;

public class ForbiddenOrderAccessException extends RuntimeException {

    public ForbiddenOrderAccessException(Long orderId) {
        super("El pedido " + orderId + " no pertenece al cliente autenticado");
    }
}
