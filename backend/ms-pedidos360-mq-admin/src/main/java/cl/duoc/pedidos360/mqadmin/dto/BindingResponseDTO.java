package cl.duoc.pedidos360.mqadmin.dto;

/**
 * Binding creado en el broker.
 */
public record BindingResponseDTO(String exchange, String queue, String routingKey) {
}
