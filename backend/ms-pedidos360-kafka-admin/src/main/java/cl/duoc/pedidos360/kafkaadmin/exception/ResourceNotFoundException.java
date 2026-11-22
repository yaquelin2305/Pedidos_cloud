package cl.duoc.pedidos360.kafkaadmin.exception;

/**
 * El topico o el consumer group indicado no existe en el cluster.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
