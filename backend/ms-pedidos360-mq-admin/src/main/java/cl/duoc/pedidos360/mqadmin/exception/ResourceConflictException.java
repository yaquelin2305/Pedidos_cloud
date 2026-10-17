package cl.duoc.pedidos360.mqadmin.exception;

/**
 * El recurso ya existe en el broker y no se puede volver a crear.
 */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
