package cl.duoc.pedidos360.kafkaadmin.exception;

/**
 * La operacion es invalida para el estado actual del cluster: el broker la rechaza o el servicio
 * detecta antes que no se puede cumplir (por ejemplo, mas replicas que brokers disponibles).
 */
public class InvalidKafkaRequestException extends RuntimeException {

    public InvalidKafkaRequestException(String message) {
        super(message);
    }
}
