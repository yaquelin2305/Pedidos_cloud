package cl.duoc.pedidos360.kafkaadmin.dto;

/**
 * Resumen de un consumer group: su estado (por ejemplo STABLE o EMPTY) y cuantos consumidores
 * tiene conectados.
 */
public record ConsumerGroupSummaryDTO(String groupId, String state, int members) {
}
