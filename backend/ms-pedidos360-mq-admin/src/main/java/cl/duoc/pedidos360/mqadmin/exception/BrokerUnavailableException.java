package cl.duoc.pedidos360.mqadmin.exception;

/**
 * El administrador no pudo conectarse a RabbitMQ o el broker no respondio a tiempo.
 */
public class BrokerUnavailableException extends RuntimeException {

    public BrokerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
