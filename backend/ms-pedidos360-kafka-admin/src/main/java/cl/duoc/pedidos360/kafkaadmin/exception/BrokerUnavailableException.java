package cl.duoc.pedidos360.kafkaadmin.exception;

/**
 * El administrador no pudo conectarse a los brokers de Kafka o estos no respondieron a tiempo.
 */
public class BrokerUnavailableException extends RuntimeException {

    public BrokerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
