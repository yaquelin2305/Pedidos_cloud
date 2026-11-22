package cl.duoc.pedidos360.kafkaadmin.exception;

/**
 * La operacion romperia el sistema: el topico es parte de la topologia de la que dependen orders,
 * catalog, report y audit, o es un topico interno de Kafka.
 */
public class ProtectedResourceException extends RuntimeException {

    public ProtectedResourceException(String message) {
        super(message);
    }
}
