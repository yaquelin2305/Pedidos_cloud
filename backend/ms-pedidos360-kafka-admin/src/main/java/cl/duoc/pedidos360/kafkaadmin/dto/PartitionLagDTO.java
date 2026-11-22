package cl.duoc.pedidos360.kafkaadmin.dto;

/**
 * Progreso de un grupo en una particion: ultimo offset confirmado, ultimo offset escrito en el
 * broker y la diferencia entre ambos ({@code lag}), es decir, los mensajes que aun no consume.
 */
public record PartitionLagDTO(String topic, int partition, long committedOffset, long endOffset, long lag) {
}
