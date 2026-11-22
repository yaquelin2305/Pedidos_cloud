package cl.duoc.pedidos360.kafkaadmin.dto;

import java.util.List;

/**
 * Estado de una particion: broker lider (nulo si no hay), brokers con replica e ISR, las replicas
 * que estan al dia con el lider.
 */
public record PartitionDTO(int partition, Integer leader, List<Integer> replicas, List<Integer> isr) {
}
