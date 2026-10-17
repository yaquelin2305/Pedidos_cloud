package cl.duoc.pedidos360.mqadmin.dto;

/**
 * Datos para crear una cola. {@code durable} es verdadero si no se envia.
 */
public record QueueRequestDTO(
        String name,
        Boolean durable,
        QueueType type,
        String deadLetterExchange,
        String deadLetterRoutingKey) {

    public QueueRequestDTO {
        durable = durable == null || durable;
    }
}
