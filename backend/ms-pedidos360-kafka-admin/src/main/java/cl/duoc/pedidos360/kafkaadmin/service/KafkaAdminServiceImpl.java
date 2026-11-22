package cl.duoc.pedidos360.kafkaadmin.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
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

import cl.duoc.pedidos360.kafkaadmin.dto.BrokerDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.ClusterResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.PartitionDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicConfigRules;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicCreateRequestDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicDetailResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicSummaryDTO;
import cl.duoc.pedidos360.kafkaadmin.exception.BrokerUnavailableException;
import cl.duoc.pedidos360.kafkaadmin.exception.InvalidKafkaRequestException;
import cl.duoc.pedidos360.kafkaadmin.exception.ResourceConflictException;
import cl.duoc.pedidos360.kafkaadmin.exception.ResourceNotFoundException;

/**
 * Unica clase del servicio que habla con {@link AdminClient}. Las llamadas del cliente son
 * asincronas: aqui se esperan con {@link #await} y los errores del broker se traducen a las
 * excepciones propias del servicio. Los timeouts salen de {@code spring.kafka.admin.properties}.
 */
@Service
@RequiredArgsConstructor
public class KafkaAdminServiceImpl implements KafkaAdminService {

    private final AdminClient adminClient;

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
        String subject = topicSubject(name);
        TopicDescription topic = await(adminClient.describeTopics(List.of(name)).allTopicNames(), subject).get(name);
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
        return new TopicSummaryDTO(request.name(), request.partitions(), replicationFactor);
    }

    @Override
    public void deleteTopic(String name) {
        await(adminClient.deleteTopics(List.of(name)).all(), topicSubject(name));
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

    /**
     * Espera el resultado de una llamada al cliente. {@code subject} nombra el recurso consultado
     * para armar el mensaje del 404; es nulo en las consultas que no apuntan a un recurso.
     */
    private <T> T await(KafkaFuture<T> future, String subject) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BrokerUnavailableException("Se interrumpio la consulta a Kafka", ex);
        } catch (ExecutionException ex) {
            throw translate(ex.getCause(), subject);
        }
    }

    private RuntimeException translate(Throwable cause, String subject) {
        if (cause instanceof TopicExistsException) {
            return new ResourceConflictException(subject + " ya existe");
        }
        if (cause instanceof UnknownTopicOrPartitionException) {
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
        return new IllegalStateException("Error inesperado de Kafka: " + cause.getMessage(), cause);
    }
}
