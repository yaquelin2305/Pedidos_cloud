package cl.duoc.pedidos360.kafkaadmin.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.kafkaadmin.dto.ClusterResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicDetailResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicPath;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicSummaryDTO;
import cl.duoc.pedidos360.kafkaadmin.service.KafkaAdminService;

/**
 * API REST de administracion de Kafka. Solo valida la entrada y delega en
 * {@link KafkaAdminService}; no conoce las clases del cliente de Kafka. Los nombres de la ruta se
 * validan con las mismas reglas que los DTOs.
 */
@RestController
@RequestMapping("/api/kafka")
@PreAuthorize("hasRole('Admin')")
@RequiredArgsConstructor
public class KafkaAdminController {

    private final KafkaAdminService kafkaAdminService;

    @GetMapping("/cluster")
    public ResponseEntity<ClusterResponseDTO> getCluster() {
        return ResponseEntity.ok(kafkaAdminService.describeCluster());
    }

    @GetMapping("/topics")
    public ResponseEntity<List<TopicSummaryDTO>> listTopics() {
        return ResponseEntity.ok(kafkaAdminService.listTopics());
    }

    @GetMapping("/topics/{name}")
    public ResponseEntity<TopicDetailResponseDTO> getTopic(@PathVariable @TopicPath String name) {
        return ResponseEntity.ok(kafkaAdminService.describeTopic(name));
    }
}
