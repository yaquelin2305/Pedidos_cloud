package cl.duoc.pedidos360.kafkaadmin.dto;

import java.util.List;

/**
 * Un consumidor del grupo y las particiones que tiene asignadas.
 */
public record GroupMemberDTO(String memberId, String clientId, String host, List<TopicPartitionDTO> assignments) {
}
