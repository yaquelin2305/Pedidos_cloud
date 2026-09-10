package cl.duoc.pedidos360.catalog.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long productId) {
        super("Producto no encontrado: " + productId);
    }
}
