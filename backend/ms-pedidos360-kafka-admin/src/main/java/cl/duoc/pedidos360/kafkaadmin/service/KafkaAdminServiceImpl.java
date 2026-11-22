package cl.duoc.pedidos360.kafkaadmin.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.PolicyViolationException;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import cl.duoc.pedidos360.kafkaadmin.config.KafkaAdminProperties;
import cl.duoc.pedidos360.kafkaadmin.dto.BrokerDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.ClusterResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.ConfigChangeDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.ConfigOperation;
import cl.duoc.pedidos360.kafkaadmin.dto.ConsumerGroupDetailResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.ConsumerGroupSummaryDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.GroupMemberDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.PartitionDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.PartitionIncreaseRequestDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.PartitionLagDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicConfigResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicConfigRules;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicConfigUpdateRequestDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicCreateRequestDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicDetailResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicPartitionDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicSummaryDTO;
import cl.duoc.pedidos360.kafkaadmin.exception.BrokerUnavailableException;
import cl.duoc.pedidos360.kafkaadmin.exception.InvalidKafkaRequestException;
import cl.duoc.pedidos360.kafkaadmin.exception.KafkaOperationException;
import cl.duoc.pedidos360.kafkaadmin.exception.ProtectedResourceException;
import cl.duoc.pedidos360.kafkaadmin.exception.ResourceConflictException;
import cl.duoc.pedidos360.kafkaadmin.exception.ResourceNotFoundException;

/**
 * Unica clase del servicio que habla con {@link AdminClient}. Las llamadas del cliente son
 * asincronas: aqui se esperan con {@link #await} y los errores del broker se traducen a las
 * excepciones propias del servicio. Los timeouts salen de {@code spring.kafka.admin.properties}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaAdminServiceImpl implements KafkaAdminService {

    private final AdminClient adminClient;
    private final KafkaAdminProperties properties;

    @Override
    public ClusterResponseDTO describeCluster() {
        DescribeClusterResult result = adminClient.describeCluster();
        String clusterId = await(result.clusterId(), null);
        Node controller = await(result.controller(), null);
        List<BrokerDTO> brokers = await(result.nodes(), null).stream()
                .sorted(Comparator.comparingInt(Node::id))
                .map(node -> new BrokerDTO(node.id(), node.host(), node.port()))
                .toList();
        Integer controllerId = controller == null || controller.isEmpty() ? null : controller.id();
        return new ClusterResponseDTO(clusterId, controllerId, brokers);
    }

    @Override
    public List<TopicSummaryDTO> listTopics() {
        Set<String> names = await(adminClient.listTopics(new ListTopicsOptions().listInternal(false)).names(), null);
        if (names.isEmpty()) {
            return List.of();
        }
        return await(adminClient.describeTopics(names).allTopicNames(), null).values().stream()
                .filter(topic -> !topic.name().startsWith("_"))
                .map(topic -> new TopicSummaryDTO(topic.name(), topic.partitions().size(), replicationFactorOf(topic)))
                .sorted(Comparator.comparing(TopicSummaryDTO::name))
                .toList();
    }

    @Override
    public TopicDetailResponseDTO describeTopic(String name) {
        TopicDescription topic = describeRaw(name);
        List<PartitionDTO> partitions = topic.partitions().stream()
                .sorted(Comparator.comparingInt(TopicPartitionInfo::partition))
                .map(this::toPartitionDto)
                .toList();
        return new TopicDetailResponseDTO(name, topic.partitions().size(), replicationFactorOf(topic), partitions,
                readConfigs(name));
    }

    @Override
    public TopicSummaryDTO createTopic(TopicCreateRequestDTO request) {
        int brokers = await(adminClient.describeCluster().nodes(), null).size();
        int replicationFactor = request.replicationFactor();
        if (replicationFactor > brokers) {
            throw new InvalidKafkaRequestException("replicationFactor (" + replicationFactor
                    + ") no puede superar los brokers disponibles (" + brokers + ")");
        }
        String minInsync = request.configs().get(TopicConfigRules.MIN_INSYNC_REPLICAS);
        if (minInsync != null && Long.parseLong(minInsync) > replicationFactor) {
            throw new InvalidKafkaRequestException(TopicConfigRules.MIN_INSYNC_REPLICAS + " (" + minInsync
                    + ") no puede superar el replicationFactor del topico (" + replicationFactor + ")");
        }
        NewTopic topic = new NewTopic(request.name(), request.partitions(), (short) replicationFactor)
                .configs(request.configs());
        await(adminClient.createTopics(List.of(topic)).all(), topicSubject(request.name()));
        log.info("Topico '{}' creado: {} particiones, factor de replicacion {}, configs {}", request.name(),
                request.partitions(), replicationFactor, request.configs());
        return new TopicSummaryDTO(request.name(), request.partitions(), replicationFactor);
    }

    @Override
    public TopicConfigResponseDTO updateTopicConfigs(String name, TopicConfigUpdateRequestDTO request) {
        int replicationFactor = replicationFactorOf(describeRaw(name));
        for (ConfigChangeDTO change : request.changes()) {
            if (change.operation() == ConfigOperation.SET
                    && TopicConfigRules.MIN_INSYNC_REPLICAS.equals(change.key())
                    && Long.parseLong(change.value()) > replicationFactor) {
                throw new InvalidKafkaRequestException(TopicConfigRules.MIN_INSYNC_REPLICAS + " (" + change.value()
                        + ") no puede superar el replicationFactor del topico (" + replicationFactor + ")");
            }
        }
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, name);
        Collection<AlterConfigOp> operations = request.changes().stream().map(this::toAlterConfigOp).toList();
        Map<ConfigResource, Collection<AlterConfigOp>> alterations = Map.of(resource, operations);
        await(adminClient.incrementalAlterConfigs(alterations).all(), topicSubject(name));
        log.info("Configuracion del topico '{}' modificada: {}", name, request.changes());
        return new TopicConfigResponseDTO(name, readConfigs(name));
    }

    @Override
    public TopicSummaryDTO increasePartitions(String name, PartitionIncreaseRequestDTO request) {
        TopicDescription topic = describeRaw(name);
        int current = topic.partitions().size();
        int total = request.totalCount();
        if (total <= current) {
            throw new InvalidKafkaRequestException("totalCount (" + total + ") debe ser mayor que las particiones "
                    + "actuales (" + current + "): Kafka no permite reducir las particiones de un topico");
        }
        await(adminClient.createPartitions(Map.of(name, NewPartitions.increaseTo(total))).all(), topicSubject(name));
        log.info("Topico '{}': particiones aumentadas de {} a {}", name, current, total);
        return new TopicSummaryDTO(name, total, replicationFactorOf(topic));
    }

    @Override
    public void deleteTopic(String name) {
        if (properties.isProtected(name)) {
            throw new ProtectedResourceException(topicSubject(name)
                    + " es parte del sistema y no se puede eliminar; si se puede modificar su configuracion");
        }
        await(adminClient.deleteTopics(List.of(name)).all(), topicSubject(name));
        log.info("Topico '{}' eliminado", name);
    }

    @Override
    public List<ConsumerGroupSummaryDTO> listConsumerGroups() {
        List<String> groupIds = await(adminClient.listConsumerGroups().all(), null).stream()
                .map(ConsumerGroupListing::groupId)
                .sorted()
                .toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }
        Map<String, ConsumerGroupDescription> groups = await(adminClient.describeConsumerGroups(groupIds).all(), null);
        return groupIds.stream()
                .map(groups::get)
                .map(group -> new ConsumerGroupSummaryDTO(group.groupId(), group.state().name(), group.members().size()))
                .toList();
    }

    @Override
    public ConsumerGroupDetailResponseDTO describeConsumerGroup(String groupId) {
        String subject = groupSubject(groupId);
        ConsumerGroupDescription group = await(adminClient.describeConsumerGroups(List.of(groupId)).all(), subject)
                .get(groupId);
        // Para un grupo que no existe Kafka no falla: lo describe como DEAD y sin miembros.
        if (group == null || group.state() == ConsumerGroupState.DEAD) {
            throw new ResourceNotFoundException(subject + " no existe");
        }
        List<PartitionLagDTO> offsets = readLag(groupId, subject);
        long totalLag = offsets.stream().mapToLong(PartitionLagDTO::lag).sum();
        List<GroupMemberDTO> members = group.members().stream()
                .sorted(Comparator.comparing(MemberDescription::consumerId))
                .map(this::toMemberDto)
                .toList();
        return new ConsumerGroupDetailResponseDTO(groupId, group.state().name(), members, offsets, totalLag);
    }

    /** Lag por particion: ultimo offset escrito por el broker menos el ultimo confirmado por el grupo. */
    private List<PartitionLagDTO> readLag(String groupId, String subject) {
        Map<TopicPartition, OffsetAndMetadata> committed =
                await(adminClient.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata(), subject);
        if (committed.isEmpty()) {
            return List.of();
        }
        Map<TopicPartition, OffsetSpec> latest = committed.keySet().stream()
                .collect(Collectors.toMap(partition -> partition, partition -> OffsetSpec.latest()));
        // Un topico con offsets confirmados puede haberse eliminado: el 404 debe nombrar al topico, no al grupo.
        Map<TopicPartition, ListOffsetsResultInfo> endOffsets = await(adminClient.listOffsets(latest).all(),
                "Un topico del consumer group '" + groupId + "'");
        return committed.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .sorted(Comparator.comparing((Map.Entry<TopicPartition, OffsetAndMetadata> entry) -> entry.getKey().topic())
                        .thenComparingInt(entry -> entry.getKey().partition()))
                .map(entry -> {
                    long committedOffset = entry.getValue().offset();
                    long endOffset = endOffsets.get(entry.getKey()).offset();
                    return new PartitionLagDTO(entry.getKey().topic(), entry.getKey().partition(), committedOffset,
                            endOffset, Math.max(0, endOffset - committedOffset));
                })
                .toList();
    }

    private GroupMemberDTO toMemberDto(MemberDescription member) {
        List<TopicPartitionDTO> assignments = member.assignment().topicPartitions().stream()
                .sorted(Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition))
                .map(partition -> new TopicPartitionDTO(partition.topic(), partition.partition()))
                .toList();
        return new GroupMemberDTO(member.consumerId(), member.clientId(), member.host(), assignments);
    }

    private TopicDescription describeRaw(String name) {
        return await(adminClient.describeTopics(List.of(name)).allTopicNames(), topicSubject(name)).get(name);
    }

    private AlterConfigOp toAlterConfigOp(ConfigChangeDTO change) {
        return switch (change.operation()) {
            case SET -> new AlterConfigOp(new ConfigEntry(change.key(), change.value()), AlterConfigOp.OpType.SET);
            case DELETE -> new AlterConfigOp(new ConfigEntry(change.key(), null), AlterConfigOp.OpType.DELETE);
        };
    }

    private Map<String, String> readConfigs(String topicName) {
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
        Config config = await(adminClient.describeConfigs(List.of(resource)).all(), topicSubject(topicName))
                .get(resource);
        Map<String, String> configs = new LinkedHashMap<>();
        for (String key : TopicConfigRules.ALLOWED_KEYS) {
            ConfigEntry entry = config.get(key);
            if (entry != null && entry.value() != null) {
                configs.put(key, entry.value());
            }
        }
        return configs;
    }

    private PartitionDTO toPartitionDto(TopicPartitionInfo info) {
        Integer leader = info.leader() == null || info.leader().isEmpty() ? null : info.leader().id();
        return new PartitionDTO(info.partition(), leader, nodeIds(info.replicas()), nodeIds(info.isr()));
    }

    private static List<Integer> nodeIds(List<Node> nodes) {
        return nodes.stream().map(Node::id).sorted().toList();
    }

    private static int replicationFactorOf(TopicDescription topic) {
        return topic.partitions().isEmpty() ? 0 : topic.partitions().get(0).replicas().size();
    }

    private static String topicSubject(String name) {
        return "El topico '" + name + "'";
    }

    private static String groupSubject(String groupId) {
        return "El consumer group '" + groupId + "'";
    }

    /**
     * Espera el resultado de una llamada al cliente. {@code subject} nombra el recurso consultado
     * para armar el mensaje del 404 o del 409; si es nulo (consultas que no apuntan a un recurso
     * concreto) se usa un texto generico.
     */
    private <T> T await(KafkaFuture<T> future, String subject) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BrokerUnavailableException("Se interrumpio la consulta a Kafka", ex);
        } catch (ExecutionException ex) {
            throw translate(ex.getCause(), subject == null ? "El recurso solicitado" : subject);
        }
    }

    private RuntimeException translate(Throwable cause, String subject) {
        if (cause instanceof TopicExistsException) {
            return new ResourceConflictException(subject + " ya existe");
        }
        if (cause instanceof UnknownTopicOrPartitionException || cause instanceof GroupIdNotFoundException) {
            return new ResourceNotFoundException(subject + " no existe");
        }
        if (cause instanceof InvalidPartitionsException || cause instanceof InvalidReplicationFactorException
                || cause instanceof InvalidConfigurationException || cause instanceof PolicyViolationException
                || cause instanceof InvalidRequestException) {
            return new InvalidKafkaRequestException(cause.getMessage());
        }
        if (cause instanceof RetriableException) {
            return new BrokerUnavailableException("Kafka no esta disponible o no respondio a tiempo", cause);
        }
        return new KafkaOperationException("Error inesperado de Kafka: " + cause.getMessage(), cause);
    }
}
