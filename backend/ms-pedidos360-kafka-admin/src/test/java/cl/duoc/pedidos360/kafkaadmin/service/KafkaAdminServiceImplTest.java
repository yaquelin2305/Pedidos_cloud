package cl.duoc.pedidos360.kafkaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.AlterConfigsResult;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.CreatePartitionsResult;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.MemberAssignment;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.duoc.pedidos360.kafkaadmin.config.KafkaAdminProperties;
import cl.duoc.pedidos360.kafkaadmin.dto.ClusterResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.ConfigChangeDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.ConfigOperation;
import cl.duoc.pedidos360.kafkaadmin.dto.ConsumerGroupDetailResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.ConsumerGroupSummaryDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.PartitionIncreaseRequestDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.PartitionLagDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicConfigResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicConfigUpdateRequestDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicCreateRequestDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicDetailResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicSummaryDTO;
import cl.duoc.pedidos360.kafkaadmin.exception.BrokerUnavailableException;
import cl.duoc.pedidos360.kafkaadmin.exception.InvalidKafkaRequestException;
import cl.duoc.pedidos360.kafkaadmin.exception.KafkaOperationException;
import cl.duoc.pedidos360.kafkaadmin.exception.ProtectedResourceException;
import cl.duoc.pedidos360.kafkaadmin.exception.ResourceConflictException;
import cl.duoc.pedidos360.kafkaadmin.exception.ResourceNotFoundException;

/**
 * Prueba el servicio con {@link AdminClient} mockeado. Los resultados del cliente son futuros: los
 * exitosos se entregan ya completados y los errores del broker como un futuro fallido, que es como
 * llegan al servicio en un cluster real (envueltos en una ExecutionException).
 */
@ExtendWith(MockitoExtension.class)
class KafkaAdminServiceImplTest {

    private static final String PROTECTED_TOPIC = "pedidos360.orders.events";

    private static final Node BROKER_1 = new Node(1, "kafka-1", 29092);
    private static final Node BROKER_2 = new Node(2, "kafka-2", 29092);
    private static final Node BROKER_3 = new Node(3, "kafka-3", 29092);

    @Mock
    private AdminClient adminClient;

    private KafkaAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KafkaAdminServiceImpl(adminClient, new KafkaAdminProperties(Set.of(PROTECTED_TOPIC)));
    }

    private static <T> KafkaFuture<T> ok(T value) {
        return KafkaFuture.completedFuture(value);
    }

    private static <T> KafkaFuture<T> failed(Throwable error) {
        KafkaFutureImpl<T> future = new KafkaFutureImpl<>();
        future.completeExceptionally(error);
        return future;
    }

    /** Topico con las particiones y el factor de replicacion pedidos, lideradas por el broker 1. */
    private static TopicDescription topic(String name, int partitions, int replicationFactor) {
        List<Node> replicas = List.of(BROKER_1, BROKER_2, BROKER_3).subList(0, replicationFactor);
        List<TopicPartitionInfo> infos = IntStream.range(0, partitions)
                .mapToObj(partition -> new TopicPartitionInfo(partition, BROKER_1, replicas, replicas))
                .toList();
        return new TopicDescription(name, false, infos);
    }

    private void stubBrokers(Node... nodes) {
        DescribeClusterResult cluster = mock(DescribeClusterResult.class);
        when(cluster.nodes()).thenReturn(ok(List.of(nodes)));
        when(adminClient.describeCluster()).thenReturn(cluster);
    }

    private void stubTopic(String name, TopicDescription description) {
        DescribeTopicsResult result = mock(DescribeTopicsResult.class);
        when(result.allTopicNames()).thenReturn(ok(Map.of(name, description)));
        when(adminClient.describeTopics(List.of(name))).thenReturn(result);
    }

    private void stubMissingTopic(String name) {
        DescribeTopicsResult result = mock(DescribeTopicsResult.class);
        when(result.allTopicNames()).thenReturn(failed(new UnknownTopicOrPartitionException()));
        when(adminClient.describeTopics(List.of(name))).thenReturn(result);
    }

    private void stubTopicConfigs(String name, ConfigEntry... entries) {
        DescribeConfigsResult result = mock(DescribeConfigsResult.class);
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, name);
        when(result.all()).thenReturn(ok(Map.of(resource, new Config(List.of(entries)))));
        when(adminClient.describeConfigs(List.of(resource))).thenReturn(result);
    }

    @Test
    void describesClusterWithSortedBrokers() {
        DescribeClusterResult cluster = mock(DescribeClusterResult.class);
        when(cluster.clusterId()).thenReturn(ok("cluster-abc"));
        when(cluster.controller()).thenReturn(ok(BROKER_2));
        when(cluster.nodes()).thenReturn(ok(List.of(BROKER_3, BROKER_1, BROKER_2)));
        when(adminClient.describeCluster()).thenReturn(cluster);

        ClusterResponseDTO response = service.describeCluster();

        assertThat(response.clusterId()).isEqualTo("cluster-abc");
        assertThat(response.controllerId()).isEqualTo(2);
        assertThat(response.brokers()).extracting("id").containsExactly(1, 2, 3);
        assertThat(response.brokers().get(0).host()).isEqualTo("kafka-1");
        assertThat(response.brokers().get(0).port()).isEqualTo(29092);
    }

    @Test
    void listsTopicsSortedWithoutInternalOnes() {
        ListTopicsResult listing = mock(ListTopicsResult.class);
        when(listing.names()).thenReturn(ok(Set.of("zeta.events", "alpha.events")));
        ArgumentCaptor<ListTopicsOptions> options = ArgumentCaptor.forClass(ListTopicsOptions.class);
        when(adminClient.listTopics(options.capture())).thenReturn(listing);
        DescribeTopicsResult described = mock(DescribeTopicsResult.class);
        when(described.allTopicNames()).thenReturn(ok(Map.of(
                "zeta.events", topic("zeta.events", 1, 1),
                "alpha.events", topic("alpha.events", 3, 3))));
        when(adminClient.describeTopics(any(Collection.class))).thenReturn(described);

        List<TopicSummaryDTO> topics = service.listTopics();

        assertThat(options.getValue().shouldListInternal()).isFalse();
        assertThat(topics).containsExactly(
                new TopicSummaryDTO("alpha.events", 3, 3),
                new TopicSummaryDTO("zeta.events", 1, 1));
    }

    @Test
    void describesTopicWithPartitionsAndOnlyAllowedConfigs() {
        stubTopic("demo.topic", topic("demo.topic", 2, 3));
        stubTopicConfigs("demo.topic",
                new ConfigEntry("retention.ms", "604800000"),
                new ConfigEntry("cleanup.policy", "delete"),
                new ConfigEntry("compression.type", "producer"));

        TopicDetailResponseDTO detail = service.describeTopic("demo.topic");

        assertThat(detail.partitions()).isEqualTo(2);
        assertThat(detail.replicationFactor()).isEqualTo(3);
        assertThat(detail.partitionDetails()).hasSize(2);
        assertThat(detail.partitionDetails().get(0).leader()).isEqualTo(1);
        assertThat(detail.partitionDetails().get(0).replicas()).containsExactly(1, 2, 3);
        assertThat(detail.partitionDetails().get(0).isr()).containsExactly(1, 2, 3);
        assertThat(detail.configs()).containsOnlyKeys("retention.ms", "cleanup.policy");
    }

    @Test
    void describeOfMissingTopicIsNotFound() {
        stubMissingTopic("ghost");

        assertThatThrownBy(() -> service.describeTopic("ghost"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void createPassesPartitionsReplicationFactorAndConfigs() {
        stubBrokers(BROKER_1, BROKER_2, BROKER_3);
        CreateTopicsResult created = mock(CreateTopicsResult.class);
        when(created.all()).thenReturn(ok(null));
        when(adminClient.createTopics(any())).thenReturn(created);

        TopicSummaryDTO summary = service.createTopic(
                new TopicCreateRequestDTO("demo.topic", 3, 3, Map.of("retention.ms", "60000")));

        ArgumentCaptor<Collection<NewTopic>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(adminClient).createTopics(captor.capture());
        NewTopic sent = captor.getValue().iterator().next();
        assertThat(sent.name()).isEqualTo("demo.topic");
        assertThat(sent.numPartitions()).isEqualTo(3);
        assertThat(sent.replicationFactor()).isEqualTo((short) 3);
        assertThat(sent.configs()).containsEntry("retention.ms", "60000");
        assertThat(summary).isEqualTo(new TopicSummaryDTO("demo.topic", 3, 3));
    }

    @Test
    void createRejectsReplicationFactorAboveAvailableBrokers() {
        stubBrokers(BROKER_1, BROKER_2);

        assertThatThrownBy(() -> service.createTopic(new TopicCreateRequestDTO("demo.topic", 3, 3, null)))
                .isInstanceOf(InvalidKafkaRequestException.class)
                .hasMessageContaining("replicationFactor");

        verify(adminClient, never()).createTopics(any());
    }

    @Test
    void createRejectsMinInsyncReplicasAboveReplicationFactor() {
        stubBrokers(BROKER_1, BROKER_2, BROKER_3);

        assertThatThrownBy(() -> service.createTopic(
                new TopicCreateRequestDTO("demo.topic", 3, 2, Map.of("min.insync.replicas", "3"))))
                .isInstanceOf(InvalidKafkaRequestException.class)
                .hasMessageContaining("min.insync.replicas");

        verify(adminClient, never()).createTopics(any());
    }

    @Test
    void createOfExistingTopicIsConflict() {
        stubBrokers(BROKER_1, BROKER_2, BROKER_3);
        CreateTopicsResult created = mock(CreateTopicsResult.class);
        when(created.all()).thenReturn(failed(new TopicExistsException("exists")));
        when(adminClient.createTopics(any())).thenReturn(created);

        assertThatThrownBy(() -> service.createTopic(new TopicCreateRequestDTO("demo.topic", 3, 3, null)))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("demo.topic");
    }

    @Test
    void deleteOfSystemTopicIsRejectedWithoutCallingTheBroker() {
        assertThatThrownBy(() -> service.deleteTopic(PROTECTED_TOPIC))
                .isInstanceOf(ProtectedResourceException.class)
                .hasMessageContaining(PROTECTED_TOPIC);

        verifyNoMoreInteractions(adminClient);
    }

    @Test
    void deleteOfInternalTopicIsRejected() {
        assertThatThrownBy(() -> service.deleteTopic("__consumer_offsets"))
                .isInstanceOf(ProtectedResourceException.class);

        verifyNoMoreInteractions(adminClient);
    }

    @Test
    void deleteOfMissingTopicIsNotFound() {
        DeleteTopicsResult deleted = mock(DeleteTopicsResult.class);
        when(deleted.all()).thenReturn(failed(new UnknownTopicOrPartitionException()));
        when(adminClient.deleteTopics(List.of("ghost"))).thenReturn(deleted);

        assertThatThrownBy(() -> service.deleteTopic("ghost"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void deletesAnExistingTopic() {
        DeleteTopicsResult deleted = mock(DeleteTopicsResult.class);
        when(deleted.all()).thenReturn(ok(null));
        when(adminClient.deleteTopics(List.of("demo.topic"))).thenReturn(deleted);

        service.deleteTopic("demo.topic");

        verify(adminClient).deleteTopics(List.of("demo.topic"));
    }

    @Test
    void updatesConfigsWithSetAndDeleteOperations() {
        stubTopic("demo.topic", topic("demo.topic", 3, 3));
        AlterConfigsResult altered = mock(AlterConfigsResult.class);
        when(altered.all()).thenReturn(ok(null));
        when(adminClient.incrementalAlterConfigs(any())).thenReturn(altered);
        stubTopicConfigs("demo.topic", new ConfigEntry("retention.ms", "60000"));

        TopicConfigResponseDTO response = service.updateTopicConfigs("demo.topic", new TopicConfigUpdateRequestDTO(List.of(
                new ConfigChangeDTO("retention.ms", ConfigOperation.SET, "60000"),
                new ConfigChangeDTO("segment.ms", ConfigOperation.DELETE, null))));

        ArgumentCaptor<Map<ConfigResource, Collection<AlterConfigOp>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(adminClient).incrementalAlterConfigs(captor.capture());
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, "demo.topic");
        List<AlterConfigOp> operations = List.copyOf(captor.getValue().get(resource));
        assertThat(operations).hasSize(2);
        assertThat(operations.get(0).opType()).isEqualTo(AlterConfigOp.OpType.SET);
        assertThat(operations.get(0).configEntry().name()).isEqualTo("retention.ms");
        assertThat(operations.get(0).configEntry().value()).isEqualTo("60000");
        assertThat(operations.get(1).opType()).isEqualTo(AlterConfigOp.OpType.DELETE);
        assertThat(operations.get(1).configEntry().name()).isEqualTo("segment.ms");
        assertThat(response.configs()).containsEntry("retention.ms", "60000");
    }

    @Test
    void updateRejectsMinInsyncReplicasAboveReplicationFactor() {
        stubTopic("demo.topic", topic("demo.topic", 3, 2));

        assertThatThrownBy(() -> service.updateTopicConfigs("demo.topic", new TopicConfigUpdateRequestDTO(List.of(
                new ConfigChangeDTO("min.insync.replicas", ConfigOperation.SET, "3")))))
                .isInstanceOf(InvalidKafkaRequestException.class)
                .hasMessageContaining("min.insync.replicas");

        verify(adminClient, never()).incrementalAlterConfigs(any());
    }

    @Test
    void updateOfMissingTopicIsNotFound() {
        stubMissingTopic("ghost");

        assertThatThrownBy(() -> service.updateTopicConfigs("ghost", new TopicConfigUpdateRequestDTO(List.of(
                new ConfigChangeDTO("retention.ms", ConfigOperation.SET, "60000")))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void increasesPartitionsToAHigherTotal() {
        stubTopic("demo.topic", topic("demo.topic", 3, 3));
        CreatePartitionsResult created = mock(CreatePartitionsResult.class);
        when(created.all()).thenReturn(ok(null));
        when(adminClient.createPartitions(any())).thenReturn(created);

        TopicSummaryDTO summary = service.increasePartitions("demo.topic", new PartitionIncreaseRequestDTO(6));

        ArgumentCaptor<Map<String, NewPartitions>> captor = ArgumentCaptor.forClass(Map.class);
        verify(adminClient).createPartitions(captor.capture());
        assertThat(captor.getValue().get("demo.topic").totalCount()).isEqualTo(6);
        assertThat(summary).isEqualTo(new TopicSummaryDTO("demo.topic", 6, 3));
    }

    @Test
    void increaseToFewerOrEqualPartitionsIsRejectedBeforeCallingTheBroker() {
        stubTopic("demo.topic", topic("demo.topic", 3, 3));

        assertThatThrownBy(() -> service.increasePartitions("demo.topic", new PartitionIncreaseRequestDTO(2)))
                .isInstanceOf(InvalidKafkaRequestException.class)
                .hasMessageContaining("mayor");
        assertThatThrownBy(() -> service.increasePartitions("demo.topic", new PartitionIncreaseRequestDTO(3)))
                .isInstanceOf(InvalidKafkaRequestException.class);

        verify(adminClient, never()).createPartitions(any());
    }

    @Test
    void brokerRejectionOfPartitionsIsInvalidRequest() {
        stubTopic("demo.topic", topic("demo.topic", 3, 3));
        CreatePartitionsResult created = mock(CreatePartitionsResult.class);
        when(created.all()).thenReturn(failed(new InvalidPartitionsException("rechazado")));
        when(adminClient.createPartitions(any())).thenReturn(created);

        assertThatThrownBy(() -> service.increasePartitions("demo.topic", new PartitionIncreaseRequestDTO(6)))
                .isInstanceOf(InvalidKafkaRequestException.class)
                .hasMessage("rechazado");
    }

    @Test
    void unreachableBrokersAreReportedAsUnavailable() {
        DescribeClusterResult cluster = mock(DescribeClusterResult.class);
        when(cluster.clusterId()).thenReturn(failed(new TimeoutException("sin respuesta")));
        when(adminClient.describeCluster()).thenReturn(cluster);

        assertThatThrownBy(() -> service.describeCluster()).isInstanceOf(BrokerUnavailableException.class);
    }

    @Test
    void listsConsumerGroupsWithStateAndMemberCount() {
        ListConsumerGroupsResult listing = mock(ListConsumerGroupsResult.class);
        when(listing.all()).thenReturn(ok(List.of(
                new ConsumerGroupListing("pedidos360-report", false),
                new ConsumerGroupListing("pedidos360-audit", false))));
        when(adminClient.listConsumerGroups()).thenReturn(listing);
        DescribeConsumerGroupsResult described = mock(DescribeConsumerGroupsResult.class);
        when(described.all()).thenReturn(ok(Map.of(
                "pedidos360-audit", group("pedidos360-audit", ConsumerGroupState.STABLE, member("audit-1", 0)),
                "pedidos360-report", group("pedidos360-report", ConsumerGroupState.STABLE,
                        member("report-1", 0), member("report-2", 1), member("report-3", 2)))));
        when(adminClient.describeConsumerGroups(List.of("pedidos360-audit", "pedidos360-report")))
                .thenReturn(described);

        List<ConsumerGroupSummaryDTO> groups = service.listConsumerGroups();

        assertThat(groups).containsExactly(
                new ConsumerGroupSummaryDTO("pedidos360-audit", "STABLE", 1),
                new ConsumerGroupSummaryDTO("pedidos360-report", "STABLE", 3));
    }

    @Test
    void computesLagAsEndOffsetMinusCommittedOffset() {
        stubGroup("pedidos360-report", group("pedidos360-report", ConsumerGroupState.STABLE,
                member("report-1", 0), member("report-2", 1)));
        TopicPartition partition0 = new TopicPartition("pedidos360.orders.events", 0);
        TopicPartition partition1 = new TopicPartition("pedidos360.orders.events", 1);
        TopicPartition partition2 = new TopicPartition("pedidos360.orders.events", 2);
        ListConsumerGroupOffsetsResult committed = mock(ListConsumerGroupOffsetsResult.class);
        when(committed.partitionsToOffsetAndMetadata()).thenReturn(ok(Map.of(
                partition0, new OffsetAndMetadata(5),
                partition1, new OffsetAndMetadata(10),
                partition2, new OffsetAndMetadata(0))));
        when(adminClient.listConsumerGroupOffsets("pedidos360-report")).thenReturn(committed);
        ListOffsetsResult ends = mock(ListOffsetsResult.class);
        when(ends.all()).thenReturn(ok(Map.of(
                partition0, new ListOffsetsResultInfo(8, 0, Optional.empty()),
                partition1, new ListOffsetsResultInfo(10, 0, Optional.empty()),
                partition2, new ListOffsetsResultInfo(4, 0, Optional.empty()))));
        when(adminClient.listOffsets(any())).thenReturn(ends);

        ConsumerGroupDetailResponseDTO detail = service.describeConsumerGroup("pedidos360-report");

        assertThat(detail.offsets()).containsExactly(
                new PartitionLagDTO("pedidos360.orders.events", 0, 5, 8, 3),
                new PartitionLagDTO("pedidos360.orders.events", 1, 10, 10, 0),
                new PartitionLagDTO("pedidos360.orders.events", 2, 0, 4, 4));
        assertThat(detail.totalLag()).isEqualTo(7);
        assertThat(detail.state()).isEqualTo("STABLE");
        assertThat(detail.members()).hasSize(2);
        assertThat(detail.members().get(0).memberId()).isEqualTo("report-1");
        assertThat(detail.members().get(0).assignments()).extracting("partition").containsExactly(0);
    }

    @Test
    void groupWithoutCommittedOffsetsHasZeroLag() {
        stubGroup("pedidos360-audit", group("pedidos360-audit", ConsumerGroupState.EMPTY));
        ListConsumerGroupOffsetsResult committed = mock(ListConsumerGroupOffsetsResult.class);
        when(committed.partitionsToOffsetAndMetadata()).thenReturn(ok(Map.of()));
        when(adminClient.listConsumerGroupOffsets("pedidos360-audit")).thenReturn(committed);

        ConsumerGroupDetailResponseDTO detail = service.describeConsumerGroup("pedidos360-audit");

        assertThat(detail.offsets()).isEmpty();
        assertThat(detail.totalLag()).isZero();
        assertThat(detail.members()).isEmpty();
    }

    @Test
    void unknownConsumerGroupIsNotFound() {
        stubGroup("ghost", group("ghost", ConsumerGroupState.DEAD));

        assertThatThrownBy(() -> service.describeConsumerGroup("ghost"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost");

        verify(adminClient, never()).listConsumerGroupOffsets(any(String.class));
    }

    @Test
    void topicDeletedBetweenListAndDescribeIsNotFoundWithAReadableMessage() {
        ListTopicsResult listing = mock(ListTopicsResult.class);
        when(listing.names()).thenReturn(ok(Set.of("vanishing.topic")));
        when(adminClient.listTopics(any(ListTopicsOptions.class))).thenReturn(listing);
        DescribeTopicsResult described = mock(DescribeTopicsResult.class);
        when(described.allTopicNames()).thenReturn(failed(new UnknownTopicOrPartitionException()));
        when(adminClient.describeTopics(any(Collection.class))).thenReturn(described);

        assertThatThrownBy(() -> service.listTopics())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("El recurso solicitado no existe");
    }

    @Test
    void topicDeletedWhileReadingLagNamesTheTopicNotTheGroup() {
        stubGroup("pedidos360-report", group("pedidos360-report", ConsumerGroupState.STABLE));
        TopicPartition partition = new TopicPartition("gone.topic", 0);
        ListConsumerGroupOffsetsResult committed = mock(ListConsumerGroupOffsetsResult.class);
        when(committed.partitionsToOffsetAndMetadata()).thenReturn(ok(Map.of(partition, new OffsetAndMetadata(3))));
        when(adminClient.listConsumerGroupOffsets("pedidos360-report")).thenReturn(committed);
        ListOffsetsResult ends = mock(ListOffsetsResult.class);
        when(ends.all()).thenReturn(failed(new UnknownTopicOrPartitionException()));
        when(adminClient.listOffsets(any())).thenReturn(ends);

        assertThatThrownBy(() -> service.describeConsumerGroup("pedidos360-report"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Un topico del consumer group 'pedidos360-report' no existe");
    }

    @Test
    void unclassifiedBrokerErrorsAreReportedAsOperationFailures() {
        DeleteTopicsResult deleted = mock(DeleteTopicsResult.class);
        when(deleted.all()).thenReturn(failed(new AuthorizationException("sin permiso")));
        when(adminClient.deleteTopics(List.of("demo.topic"))).thenReturn(deleted);

        assertThatThrownBy(() -> service.deleteTopic("demo.topic"))
                .isInstanceOf(KafkaOperationException.class)
                .hasMessageContaining("sin permiso");
    }

    private void stubGroup(String groupId, ConsumerGroupDescription description) {
        DescribeConsumerGroupsResult result = mock(DescribeConsumerGroupsResult.class);
        when(result.all()).thenReturn(ok(Map.of(groupId, description)));
        when(adminClient.describeConsumerGroups(List.of(groupId))).thenReturn(result);
    }

    private static ConsumerGroupDescription group(String groupId, ConsumerGroupState state,
            MemberDescription... members) {
        return new ConsumerGroupDescription(groupId, false, List.of(members), "range", state, BROKER_1);
    }

    private static MemberDescription member(String id, int partition) {
        return new MemberDescription(id, "client-" + id, "/172.18.0.9",
                new MemberAssignment(Set.of(new TopicPartition("pedidos360.orders.events", partition))));
    }
}
