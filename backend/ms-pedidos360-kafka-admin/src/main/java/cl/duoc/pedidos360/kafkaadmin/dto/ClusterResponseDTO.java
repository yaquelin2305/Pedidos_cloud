package cl.duoc.pedidos360.kafkaadmin.dto;

import java.util.List;

/**
 * Identidad del cluster, su controller actual (nulo si aun no hay uno elegido) y sus brokers.
 */
public record ClusterResponseDTO(String clusterId, Integer controllerId, List<BrokerDTO> brokers) {
}
