package cl.duoc.pedidos360.mqadmin.dto;

/**
 * Exchange creado en el broker.
 */
public record ExchangeResponseDTO(String name, ExchangeType type, boolean durable) {
}
