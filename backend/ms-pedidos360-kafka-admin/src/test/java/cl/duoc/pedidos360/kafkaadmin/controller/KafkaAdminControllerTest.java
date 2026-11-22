package cl.duoc.pedidos360.kafkaadmin.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import cl.duoc.pedidos360.kafkaadmin.config.SecurityConfig;
import cl.duoc.pedidos360.kafkaadmin.dto.BrokerDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.ClusterResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.ConsumerGroupDetailResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.ConsumerGroupSummaryDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.GroupMemberDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.PartitionDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.PartitionLagDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicConfigResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicDetailResponseDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicPartitionDTO;
import cl.duoc.pedidos360.kafkaadmin.dto.TopicSummaryDTO;
import cl.duoc.pedidos360.kafkaadmin.exception.BrokerUnavailableException;
import cl.duoc.pedidos360.kafkaadmin.exception.InvalidKafkaRequestException;
import cl.duoc.pedidos360.kafkaadmin.exception.KafkaOperationException;
import cl.duoc.pedidos360.kafkaadmin.exception.ProtectedResourceException;
import cl.duoc.pedidos360.kafkaadmin.exception.ResourceConflictException;
import cl.duoc.pedidos360.kafkaadmin.exception.ResourceNotFoundException;
import cl.duoc.pedidos360.kafkaadmin.service.KafkaAdminService;

/**
 * Prueba el controller con MockMvc y el servicio mockeado: codigos de exito, validacion de la
 * entrada (400 sin llegar al servicio), traduccion de errores y autorizacion. jwt() coloca las
 * authorities directamente, sin consultar al issuer real.
 */
@WebMvcTest(KafkaAdminController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-issuer.pedidos360.local")
class KafkaAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KafkaAdminService kafkaAdminService;

    @TestConfiguration
    static class JwtDecoderTestConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("no se usa: jwt() reemplaza la decodificacion en el test");
            };
        }
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return asAdmin(request).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ---- cluster y consulta de topicos

    @Test
    void returnsTheCluster() throws Exception {
        when(kafkaAdminService.describeCluster()).thenReturn(new ClusterResponseDTO("cluster-abc", 2,
                List.of(new BrokerDTO(1, "kafka-1", 29092), new BrokerDTO(2, "kafka-2", 29092))));

        mockMvc.perform(asAdmin(get("/api/kafka/cluster")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterId").value("cluster-abc"))
                .andExpect(jsonPath("$.controllerId").value(2))
                .andExpect(jsonPath("$.brokers.length()").value(2))
                .andExpect(jsonPath("$.brokers[0].host").value("kafka-1"));
    }

    @Test
    void listsTopics() throws Exception {
        when(kafkaAdminService.listTopics()).thenReturn(List.of(new TopicSummaryDTO("pedidos360.orders.events", 3, 3)));

        mockMvc.perform(asAdmin(get("/api/kafka/topics")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("pedidos360.orders.events"))
                .andExpect(jsonPath("$[0].partitions").value(3))
                .andExpect(jsonPath("$[0].replicationFactor").value(3));
    }

    @Test
    void describesATopic() throws Exception {
        when(kafkaAdminService.describeTopic("pedidos360.orders.events")).thenReturn(new TopicDetailResponseDTO(
                "pedidos360.orders.events", 1, 3,
                List.of(new PartitionDTO(0, 1, List.of(1, 2, 3), List.of(1, 2, 3))),
                Map.of("retention.ms", "604800000")));

        mockMvc.perform(asAdmin(get("/api/kafka/topics/pedidos360.orders.events")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partitionDetails[0].leader").value(1))
                .andExpect(jsonPath("$.partitionDetails[0].isr.length()").value(3))
                .andExpect(jsonPath("$.configs['retention.ms']").value("604800000"));
    }

    @Test
    void missingTopicIsNotFound() throws Exception {
        when(kafkaAdminService.describeTopic("ghost"))
                .thenThrow(new ResourceNotFoundException("El topico 'ghost' no existe"));

        mockMvc.perform(asAdmin(get("/api/kafka/topics/ghost")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("El topico 'ghost' no existe"));
    }

    @Test
    void topicNameInThePathIsValidated() throws Exception {
        mockMvc.perform(asAdmin(get("/api/kafka/topics/{name}", "bad name")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("solo admite letras")));

        verifyNoInteractions(kafkaAdminService);
    }

    @Test
    void unreachableBrokersAreServiceUnavailable() throws Exception {
        when(kafkaAdminService.describeCluster())
                .thenThrow(new BrokerUnavailableException("Kafka no esta disponible", new RuntimeException()));

        mockMvc.perform(asAdmin(get("/api/kafka/cluster")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void unclassifiedKafkaErrorsAreInternalErrorsWithoutLeakingTheBrokerDetail() throws Exception {
        when(kafkaAdminService.listTopics())
                .thenThrow(new KafkaOperationException("Error inesperado de Kafka: detalle interno del broker",
                        new RuntimeException()));

        mockMvc.perform(asAdmin(get("/api/kafka/topics")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Kafka devolvio un error inesperado al procesar la operacion"))
                .andExpect(jsonPath("$.path").value("/api/kafka/topics"));
    }

    // ---- crear topicos

    @Test
    void createsATopic() throws Exception {
        when(kafkaAdminService.createTopic(any())).thenReturn(new TopicSummaryDTO("demo.topic", 3, 3));

        mockMvc.perform(json(post("/api/kafka/topics"), """
                        {"name": "demo.topic", "partitions": 3, "replicationFactor": 3,
                         "configs": {"retention.ms": "60000", "cleanup.policy": "compact,delete"}}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("demo.topic"))
                .andExpect(jsonPath("$.partitions").value(3));
    }

    @Test
    void createsATopicWithoutConfigs() throws Exception {
        when(kafkaAdminService.createTopic(any())).thenReturn(new TopicSummaryDTO("demo.topic", 1, 1));

        mockMvc.perform(json(post("/api/kafka/topics"),
                        "{\"name\": \"demo.topic\", \"partitions\": 1, \"replicationFactor\": 1}"))
                .andExpect(status().isCreated());
    }

    static Stream<Arguments> invalidCreateBodies() {
        return Stream.of(
                Arguments.of("name con espacio",
                        "{\"name\": \"bad name\", \"partitions\": 3, \"replicationFactor\": 3}", "solo admite letras"),
                Arguments.of("name con prefijo _",
                        "{\"name\": \"_internal\", \"partitions\": 3, \"replicationFactor\": 3}", "no puede empezar con '_'"),
                Arguments.of("name '..'",
                        "{\"name\": \"..\", \"partitions\": 3, \"replicationFactor\": 3}", "no puede ser '.' ni '..'"),
                Arguments.of("name vacio",
                        "{\"name\": \"\", \"partitions\": 3, \"replicationFactor\": 3}", "es obligatorio"),
                Arguments.of("name de 250 caracteres",
                        "{\"name\": \"" + "a".repeat(250) + "\", \"partitions\": 3, \"replicationFactor\": 3}",
                        "admite hasta 249 caracteres"),
                Arguments.of("sin partitions",
                        "{\"name\": \"demo\", \"replicationFactor\": 3}", "partitions: es obligatorio"),
                Arguments.of("partitions en 0",
                        "{\"name\": \"demo\", \"partitions\": 0, \"replicationFactor\": 3}", "debe estar entre 1 y 12"),
                Arguments.of("partitions en 13",
                        "{\"name\": \"demo\", \"partitions\": 13, \"replicationFactor\": 3}", "debe estar entre 1 y 12"),
                Arguments.of("replicationFactor en 4",
                        "{\"name\": \"demo\", \"partitions\": 3, \"replicationFactor\": 4}", "debe estar entre 1 y 3"),
                Arguments.of("configuracion no permitida",
                        "{\"name\": \"demo\", \"partitions\": 3, \"replicationFactor\": 3, \"configs\": {\"compression.type\": \"gzip\"}}",
                        "'compression.type' no esta permitida"),
                Arguments.of("cleanup.policy invalida",
                        "{\"name\": \"demo\", \"partitions\": 3, \"replicationFactor\": 3, \"configs\": {\"cleanup.policy\": \"archive\"}}",
                        "'cleanup.policy' solo admite: delete, compact, compact,delete"),
                Arguments.of("retention.ms negativo",
                        "{\"name\": \"demo\", \"partitions\": 3, \"replicationFactor\": 3, \"configs\": {\"retention.ms\": \"-5\"}}",
                        "'retention.ms' debe ser un entero positivo"),
                Arguments.of("retention.ms no numerico",
                        "{\"name\": \"demo\", \"partitions\": 3, \"replicationFactor\": 3, \"configs\": {\"retention.ms\": \"abc\"}}",
                        "'retention.ms' debe ser un entero positivo"),
                Arguments.of("min.cleanable.dirty.ratio fuera de rango",
                        "{\"name\": \"demo\", \"partitions\": 3, \"replicationFactor\": 3, \"configs\": {\"min.cleanable.dirty.ratio\": \"1.5\"}}",
                        "decimal entre 0 y 1"),
                Arguments.of("configuracion sin valor",
                        "{\"name\": \"demo\", \"partitions\": 3, \"replicationFactor\": 3, \"configs\": {\"retention.ms\": null}}",
                        "'retention.ms' requiere un valor"),
                Arguments.of("clave que intenta inyectar una expresion",
                        "{\"name\": \"demo\", \"partitions\": 3, \"replicationFactor\": 3, \"configs\": {\"${1+1}\": \"1\"}}",
                        "'${1+1}' no esta permitida"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCreateBodies")
    void rejectsInvalidCreateBodies(String description, String body, String expectedMessage) throws Exception {
        mockMvc.perform(json(post("/api/kafka/topics"), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString(expectedMessage)));

        verifyNoInteractions(kafkaAdminService);
    }

    @Test
    void createOfExistingTopicIsConflict() throws Exception {
        when(kafkaAdminService.createTopic(any()))
                .thenThrow(new ResourceConflictException("El topico 'demo.topic' ya existe"));

        mockMvc.perform(json(post("/api/kafka/topics"),
                        "{\"name\": \"demo.topic\", \"partitions\": 3, \"replicationFactor\": 3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El topico 'demo.topic' ya existe"));
    }

    @Test
    void createAboveAvailableBrokersIsBadRequest() throws Exception {
        when(kafkaAdminService.createTopic(any()))
                .thenThrow(new InvalidKafkaRequestException("replicationFactor (3) no puede superar los brokers (2)"));

        mockMvc.perform(json(post("/api/kafka/topics"),
                        "{\"name\": \"demo.topic\", \"partitions\": 3, \"replicationFactor\": 3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("replicationFactor")));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(json(post("/api/kafka/topics"), "{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la peticion no es un JSON valido"));
    }

    // ---- modificar configuraciones

    @Test
    void updatesTopicConfigs() throws Exception {
        when(kafkaAdminService.updateTopicConfigs(any(), any()))
                .thenReturn(new TopicConfigResponseDTO("demo.topic", Map.of("retention.ms", "60000")));

        mockMvc.perform(json(patch("/api/kafka/topics/demo.topic/configs"), """
                        {"changes": [
                          {"key": "retention.ms", "operation": "SET", "value": "60000"},
                          {"key": "segment.ms", "operation": "DELETE"}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("demo.topic"))
                .andExpect(jsonPath("$.configs['retention.ms']").value("60000"));
    }

    static Stream<Arguments> invalidConfigUpdates() {
        return Stream.of(
                Arguments.of("SET sin value",
                        "{\"changes\": [{\"key\": \"retention.ms\", \"operation\": \"SET\"}]}",
                        "la operacion SET requiere un value"),
                Arguments.of("clave no permitida",
                        "{\"changes\": [{\"key\": \"unclean.leader.election.enable\", \"operation\": \"SET\", \"value\": \"true\"}]}",
                        "no esta permitida"),
                Arguments.of("cleanup.policy invalida",
                        "{\"changes\": [{\"key\": \"cleanup.policy\", \"operation\": \"SET\", \"value\": \"archive\"}]}",
                        "'cleanup.policy' solo admite"),
                Arguments.of("operation desconocida",
                        "{\"changes\": [{\"key\": \"retention.ms\", \"operation\": \"REPLACE\", \"value\": \"1\"}]}",
                        "changes[0].operation: valor 'REPLACE' invalido. Valores permitidos: SET, DELETE"),
                Arguments.of("operation ausente",
                        "{\"changes\": [{\"key\": \"retention.ms\", \"value\": \"1\"}]}", "es obligatorio: SET o DELETE"),
                Arguments.of("lista vacia", "{\"changes\": []}", "debe incluir al menos un cambio"),
                Arguments.of("sin changes", "{}", "debe incluir al menos un cambio"),
                Arguments.of("clave repetida",
                        "{\"changes\": [{\"key\": \"retention.ms\", \"operation\": \"SET\", \"value\": \"1\"},"
                                + " {\"key\": \"retention.ms\", \"operation\": \"DELETE\"}]}",
                        "no puede haber dos cambios sobre la misma configuracion"),
                Arguments.of("key vacia",
                        "{\"changes\": [{\"key\": \"\", \"operation\": \"DELETE\"}]}", "es obligatorio"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidConfigUpdates")
    void rejectsInvalidConfigUpdates(String description, String body, String expectedMessage) throws Exception {
        mockMvc.perform(json(patch("/api/kafka/topics/demo.topic/configs"), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(expectedMessage)));

        verifyNoInteractions(kafkaAdminService);
    }

    @Test
    void updateOfMissingTopicIsNotFound() throws Exception {
        when(kafkaAdminService.updateTopicConfigs(any(), any()))
                .thenThrow(new ResourceNotFoundException("El topico 'ghost' no existe"));

        mockMvc.perform(json(patch("/api/kafka/topics/ghost/configs"),
                        "{\"changes\": [{\"key\": \"retention.ms\", \"operation\": \"SET\", \"value\": \"1\"}]}"))
                .andExpect(status().isNotFound());
    }

    // ---- particiones

    @Test
    void increasesPartitions() throws Exception {
        when(kafkaAdminService.increasePartitions(any(), any())).thenReturn(new TopicSummaryDTO("demo.topic", 6, 3));

        mockMvc.perform(json(post("/api/kafka/topics/demo.topic/partitions"), "{\"totalCount\": 6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partitions").value(6));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"totalCount\": 1}", "{\"totalCount\": 13}"})
    void rejectsInvalidPartitionIncreases(String body) throws Exception {
        mockMvc.perform(json(post("/api/kafka/topics/demo.topic/partitions"), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("totalCount")));

        verifyNoInteractions(kafkaAdminService);
    }

    @Test
    void reducingPartitionsIsBadRequest() throws Exception {
        when(kafkaAdminService.increasePartitions(any(), any()))
                .thenThrow(new InvalidKafkaRequestException("totalCount (2) debe ser mayor que las actuales (3)"));

        mockMvc.perform(json(post("/api/kafka/topics/demo.topic/partitions"), "{\"totalCount\": 2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("debe ser mayor")));
    }

    // ---- eliminar topicos

    @Test
    void deletesATopic() throws Exception {
        mockMvc.perform(asAdmin(delete("/api/kafka/topics/demo.topic")))
                .andExpect(status().isNoContent());

        verify(kafkaAdminService).deleteTopic("demo.topic");
    }

    @Test
    void deleteOfMissingTopicIsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("El topico 'ghost' no existe")).when(kafkaAdminService)
                .deleteTopic("ghost");

        mockMvc.perform(asAdmin(delete("/api/kafka/topics/ghost")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteOfSystemTopicIsConflict() throws Exception {
        doThrow(new ProtectedResourceException("El topico 'pedidos360.orders.events' es parte del sistema"))
                .when(kafkaAdminService).deleteTopic("pedidos360.orders.events");

        mockMvc.perform(asAdmin(delete("/api/kafka/topics/pedidos360.orders.events")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("parte del sistema")));
    }

    @Test
    void deleteOfInternalTopicReachesTheServiceAndIsConflict() throws Exception {
        doThrow(new ProtectedResourceException("El topico '__consumer_offsets' es parte del sistema"))
                .when(kafkaAdminService).deleteTopic("__consumer_offsets");

        mockMvc.perform(asAdmin(delete("/api/kafka/topics/__consumer_offsets")))
                .andExpect(status().isConflict());
    }

    // ---- consumer groups

    @Test
    void listsConsumerGroups() throws Exception {
        when(kafkaAdminService.listConsumerGroups())
                .thenReturn(List.of(new ConsumerGroupSummaryDTO("pedidos360-report", "STABLE", 3)));

        mockMvc.perform(asAdmin(get("/api/kafka/consumer-groups")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value("pedidos360-report"))
                .andExpect(jsonPath("$[0].state").value("STABLE"))
                .andExpect(jsonPath("$[0].members").value(3));
    }

    @Test
    void describesAConsumerGroupWithLag() throws Exception {
        when(kafkaAdminService.describeConsumerGroup("pedidos360-report")).thenReturn(new ConsumerGroupDetailResponseDTO(
                "pedidos360-report", "STABLE",
                List.of(new GroupMemberDTO("report-1", "client-1", "/172.18.0.9",
                        List.of(new TopicPartitionDTO("pedidos360.orders.events", 0)))),
                List.of(new PartitionLagDTO("pedidos360.orders.events", 0, 5, 8, 3)),
                3));

        mockMvc.perform(asAdmin(get("/api/kafka/consumer-groups/pedidos360-report")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].assignments[0].partition").value(0))
                .andExpect(jsonPath("$.offsets[0].committedOffset").value(5))
                .andExpect(jsonPath("$.offsets[0].endOffset").value(8))
                .andExpect(jsonPath("$.offsets[0].lag").value(3))
                .andExpect(jsonPath("$.totalLag").value(3));
    }

    @Test
    void missingConsumerGroupIsNotFound() throws Exception {
        when(kafkaAdminService.describeConsumerGroup("ghost"))
                .thenThrow(new ResourceNotFoundException("El consumer group 'ghost' no existe"));

        mockMvc.perform(asAdmin(get("/api/kafka/consumer-groups/ghost")))
                .andExpect(status().isNotFound());
    }

    // ---- autorizacion

    static Stream<MockHttpServletRequestBuilder> allEndpoints() {
        return Stream.of(
                get("/api/kafka/cluster"),
                get("/api/kafka/topics"),
                get("/api/kafka/topics/demo.topic"),
                post("/api/kafka/topics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"demo\", \"partitions\": 3, \"replicationFactor\": 3}"),
                patch("/api/kafka/topics/demo.topic/configs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changes\": [{\"key\": \"retention.ms\", \"operation\": \"DELETE\"}]}"),
                post("/api/kafka/topics/demo.topic/partitions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCount\": 6}"),
                delete("/api/kafka/topics/demo.topic"),
                get("/api/kafka/consumer-groups"),
                get("/api/kafka/consumer-groups/pedidos360-report"));
    }

    @ParameterizedTest
    @MethodSource("allEndpoints")
    void withoutTokenIsUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(kafkaAdminService);
    }

    @ParameterizedTest
    @MethodSource("allEndpoints")
    void operatorIsForbidden(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operator"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(kafkaAdminService);
    }

    @ParameterizedTest
    @MethodSource("allEndpoints")
    void customerIsForbidden(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(kafkaAdminService);
    }
}
