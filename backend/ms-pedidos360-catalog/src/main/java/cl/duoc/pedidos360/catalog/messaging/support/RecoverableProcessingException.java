package cl.duoc.pedidos360.catalog.messaging.support;

/**
 * Fallo transitorio al procesar un mensaje valido (por ejemplo, un recurso externo no disponible).
 * {@link ManualAckHandler} lo rechaza con requeue para reintentarlo hasta el {@code x-delivery-limit}.
 */
public class RecoverableProcessingException extends RuntimeException {

    public RecoverableProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
