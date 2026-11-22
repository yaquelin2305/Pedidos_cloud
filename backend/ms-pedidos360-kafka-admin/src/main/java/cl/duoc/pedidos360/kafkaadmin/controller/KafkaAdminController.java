package cl.duoc.pedidos360.kafkaadmin.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.kafkaadmin.dto.ClusterResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.PartitionIncreaseRequestDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicConfigResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicConfigUpdateRequestDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicCreateRequestDTO;
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

    @PostMapping("/topics")
    public ResponseEntity<TopicSummaryDTO> createTopic(@Valid @RequestBody TopicCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kafkaAdminService.createTopic(request));
    }

    @PatchMapping("/topics/{name}/configs")
    public ResponseEntity<TopicConfigResponseDTO> updateTopicConfigs(@PathVariable @TopicPath String name,
            @Valid @RequestBody TopicConfigUpdateRequestDTO request) {
        return ResponseEntity.ok(kafkaAdminService.updateTopicConfigs(name, request));
    }

    @PostMapping("/topics/{name}/partitions")
    public ResponseEntity<TopicSummaryDTO> increasePartitions(@PathVariable @TopicPath String name,
            @Valid @RequestBody PartitionIncreaseRequestDTO request) {
        return ResponseEntity.ok(kafkaAdminService.increasePartitions(name, request));
    }

    @DeleteMapping("/topics/{name}")
    public ResponseEntity<Void> deleteTopic(@PathVariable @TopicPath String name) {
        kafkaAdminService.deleteTopic(name);
        return ResponseEntity.noContent().build();
    }
}
