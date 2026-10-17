package cl.duoc.pedidos360.orders.messaging.support;

/**
 * Mensaje que nunca podra procesarse aunque se reintente: JSON invalido o campos obligatorios
 * ausentes. {@link ManualAckHandler} lo rechaza sin requeue y termina en la DLQ de inmediato.
 */
public class InvalidMessageException extends IllegalArgumentException {

    public InvalidMessageException(String message) {
        super(message);
    }
}
