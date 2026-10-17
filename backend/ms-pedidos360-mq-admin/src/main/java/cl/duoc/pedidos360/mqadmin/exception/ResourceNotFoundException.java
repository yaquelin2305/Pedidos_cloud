package cl.duoc.pedidos360.mqadmin.exception;

/**
 * La cola o el exchange indicado no existe en el broker.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
