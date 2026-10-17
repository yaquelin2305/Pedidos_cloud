package cl.duoc.pedidos360.mqadmin.dto;

/**
 * Estado de una cola en el broker.
 */
public record QueueResponseDTO(String name, long messageCount, int consumerCount) {
}
