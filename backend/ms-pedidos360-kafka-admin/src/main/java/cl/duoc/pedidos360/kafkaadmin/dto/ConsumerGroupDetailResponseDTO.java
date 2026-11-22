package cl.duoc.pedidos360.kafkaadmin.dto;

import java.util.List;

/**
 * Detalle de un consumer group: sus miembros con las particiones asignadas, el lag por particion y
 * el lag total, que resume cuanto va atrasado el grupo.
 */
public record ConsumerGroupDetailResponseDTO(
        String groupId,
        String state,
        List<GroupMemberDTO> members,
        List<PartitionLagDTO> offsets,
        long totalLag) {
}
