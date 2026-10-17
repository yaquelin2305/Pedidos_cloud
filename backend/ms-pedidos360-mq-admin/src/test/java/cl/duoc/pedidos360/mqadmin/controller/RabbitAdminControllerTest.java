package cl.duoc.pedidos360.mqadmin.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

import cl.duoc.pedidos360.mqadmin.config.SecurityConfig;
import cl.duoc.pedidos360.mqadmin.dto.BindingResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.ExchangeResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.ExchangeType;
import cl.duoc.pedidos360.mqadmin.dto.QueueResponseDTO;
import cl.duoc.pedidos360.mqadmin.exception.BrokerUnavailableException;
import cl.duoc.pedidos360.mqadmin.exception.ProtectedResourceException;
import cl.duoc.pedidos360.mqadmin.exception.ResourceNotFoundException;
import cl.duoc.pedidos360.mqadmin.service.RabbitAdminService;

/**
 * Prueba el controller con MockMvc y el servicio mockeado: codigos de exito, validacion de la
 * entrada (400 sin llegar al servicio), traduccion de errores y autorizacion. jwt() coloca las
 * authorities directamente, sin consultar al issuer real.
 */
@WebMvcTest(RabbitAdminController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-issuer.pedidos360.local")
class RabbitAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RabbitAdminService rabbitAdminService;

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

    @Test
    void createsQueue() throws Exception {
        when(rabbitAdminService.createQueue(any())).thenReturn(new QueueResponseDTO("demo.alerts", 0, 0));

        mockMvc.perform(json(post("/api/mq/queues"), """
                        {"name": "demo.alerts", "type": "QUORUM", "deadLetterExchange": "pedidos360.dlx",
                         "deadLetterRoutingKey": "demo.alerts.dlq"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("demo.alerts"))
                .andExpect(jsonPath("$.messageCount").value(0));
    }

    @Test
    void returnsQueueState() throws Exception {
        when(rabbitAdminService.getQueue("demo.alerts")).thenReturn(new QueueResponseDTO("demo.alerts", 4, 1));

        mockMvc.perform(asAdmin(get("/api/mq/queues/demo.alerts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCount").value(4))
                .andExpect(jsonPath("$.consumerCount").value(1));
    }

    @Test
    void deletesQueue() throws Exception {
        mockMvc.perform(asAdmin(delete("/api/mq/queues/demo.alerts")))
                .andExpect(status().isNoContent());
        verify(rabbitAdminService).deleteQueue("demo.alerts");
    }

    @Test
    void createsExchange() throws Exception {
        when(rabbitAdminService.createExchange(any()))
                .thenReturn(new ExchangeResponseDTO("demo.events", ExchangeType.FANOUT, true));

        mockMvc.perform(json(post("/api/mq/exchanges"), """
                        {"name": "demo.events", "type": "FANOUT"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("FANOUT"))
                .andExpect(jsonPath("$.durable").value(true));
    }

    @Test
    void createsAndDeletesBinding() throws Exception {
        when(rabbitAdminService.createBinding(any()))
                .thenReturn(new BindingResponseDTO("demo.events", "demo.alerts", "demo.*"));

        mockMvc.perform(json(post("/api/mq/bindings"), """
                        {"exchange": "demo.events", "queue": "demo.alerts", "routingKey": "demo.*"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routingKey").value("demo.*"));
        mockMvc.perform(asAdmin(delete("/api/mq/bindings")
                        .param("exchange", "demo.events").param("queue", "demo.alerts").param("routingKey", "demo.*")))
                .andExpect(status().isNoContent());
        verify(rabbitAdminService).deleteBinding("demo.events", "demo.alerts", "demo.*");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"name\": \"\", \"type\": \"CLASSIC\"}",
        "{\"name\": \"amq.demo\", \"type\": \"CLASSIC\"}",
        "{\"name\": \"demo alerts\", \"type\": \"CLASSIC\"}",
        "{\"name\": \"demo.alerts\"}",
        "{\"name\": \"demo.alerts\", \"type\": \"QUORUM\", \"durable\": false}",
        "{\"name\": \"demo.alerts\", \"type\": \"CLASSIC\", \"deadLetterRoutingKey\": \"demo.dlq\"}"
    })
    void rejectsInvalidQueueRequest(String body) throws Exception {
        mockMvc.perform(json(post("/api/mq/queues"), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/mq/queues"));
        verifyNoInteractions(rabbitAdminService);
    }

    @Test
    void explainsQuorumMustBeDurable() throws Exception {
        mockMvc.perform(json(post("/api/mq/queues"), """
                        {"name": "demo.alerts", "type": "QUORUM", "durable": false}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("quorumDurable: una cola QUORUM debe ser durable"));
    }

    @Test
    void listsAllowedValuesForInvalidExchangeType() throws Exception {
        mockMvc.perform(json(post("/api/mq/exchanges"), """
                        {"name": "demo.events", "type": "BROADCAST"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "type: valor 'BROADCAST' invalido. Valores permitidos: DIRECT, TOPIC, FANOUT, HEADERS"));
        verifyNoInteractions(rabbitAdminService);
    }

    @Test
    void rejectsRoutingKeyWithUnsupportedCharacters() throws Exception {
        mockMvc.perform(json(post("/api/mq/bindings"), """
                        {"exchange": "demo.events", "queue": "demo.alerts", "routingKey": "demo.$"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("routingKey")));
        verifyNoInteractions(rabbitAdminService);
    }

    @Test
    void rejectsReservedNameInPath() throws Exception {
        mockMvc.perform(asAdmin(delete("/api/mq/exchanges/amq.direct")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("name")));
        verifyNoInteractions(rabbitAdminService);
    }

    @Test
    void reportsMissingQueue() throws Exception {
        when(rabbitAdminService.getQueue("demo.missing"))
                .thenThrow(new ResourceNotFoundException("La cola no existe: demo.missing"));

        mockMvc.perform(asAdmin(get("/api/mq/queues/demo.missing")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La cola no existe: demo.missing"));
    }

    @Test
    void reportsProtectedResourceAsConflict() throws Exception {
        doThrow(new ProtectedResourceException("La cola pedidos360.orders.tickets es parte de la topologia del sistema"))
                .when(rabbitAdminService).deleteQueue("pedidos360.orders.tickets");

        mockMvc.perform(asAdmin(delete("/api/mq/queues/pedidos360.orders.tickets")))
                .andExpect(status().isConflict());
    }

    @Test
    void reportsBrokerDownAsServiceUnavailable() throws Exception {
        when(rabbitAdminService.getQueue("demo.alerts"))
                .thenThrow(new BrokerUnavailableException("RabbitMQ no esta disponible", null));

        mockMvc.perform(asAdmin(get("/api/mq/queues/demo.alerts")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/mq/queues/demo.alerts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(rabbitAdminService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_Operator", "ROLE_Customer"})
    void rejectsNonAdminRoles(String role) throws Exception {
        mockMvc.perform(get("/api/mq/queues/demo.alerts").with(jwt().authorities(new SimpleGrantedAuthority(role))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(rabbitAdminService);
    }
}
