package cl.duoc.pedidos360.orders.exception;

/**
 * Se lanza cuando ms-pedidos360-catalog rechaza el descuento de stock de un producto
 * (stock insuficiente o producto inexistente) al intentar aceptar un pedido.
 */
public class StockUnavailableException extends RuntimeException {

    public StockUnavailableException(Long productId, String reason) {
        super("No se pudo descontar stock del producto " + productId + ": " + reason);
    }
}
