package cl.duoc.pedidos360.kafkaadmin.exception;

/**
 * Kafka respondio con un error que el servicio no clasifica (por ejemplo, de autorizacion o un
 * fallo interno del broker). Se informa como 500 sin exponer el detalle del broker al cliente.
 */
public class KafkaOperationException extends RuntimeException {

    public KafkaOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
