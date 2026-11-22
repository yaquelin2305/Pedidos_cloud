package cl.duoc.pedidos360.kafkaadmin.dto;

/**
 * Una particion de un topico.
 */
public record TopicPartitionDTO(String topic, int partition) {
}
