package cl.duoc.pedidos360.kafkaadmin.dto;

import java.util.List;
import java.util.Map;

/**
 * Detalle de un topico: su distribucion por particion y las configuraciones de la lista permitida
 * ({@link TopicConfigRules#ALLOWED_KEYS}) que tienen valor.
 */
public record TopicDetailResponseDTO(
        String name,
        int partitions,
        int replicationFactor,
        List<PartitionDTO> partitionDetails,
        Map<String, String> configs) {
}
