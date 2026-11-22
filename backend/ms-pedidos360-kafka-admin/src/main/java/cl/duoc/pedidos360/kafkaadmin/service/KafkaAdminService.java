package cl.duoc.pedidos360.kafkaadmin.service;

import java.util.List;

import cl.duoc.pedidos360.kafkaadmin.dto.ClusterResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicDetailResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicSummaryDTO;

/**
 * Operaciones de administracion del cluster Kafka. Trabaja solo con DTOs: quien lo llama no conoce
 * las clases del cliente de Kafka.
 */
public interface KafkaAdminService {

    ClusterResponseDTO describeCluster();

    /** Topicos del usuario ordenados por nombre, sin los internos de Kafka. */
    List<TopicSummaryDTO> listTopics();

    TopicDetailResponseDTO describeTopic(String name);
}
