package cl.duoc.pedidos360.kafkaadmin.exception;

/**
 * El topico ya existe en el cluster y no se puede volver a crear.
 */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
