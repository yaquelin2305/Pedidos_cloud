package cl.duoc.pedidos360.kafkaadmin.dto;

/**
 * Resumen de un topico: cuantas particiones tiene y con cuantas replicas se guarda cada una.
 */
public record TopicSummaryDTO(String name, int partitions, int replicationFactor) {
}
