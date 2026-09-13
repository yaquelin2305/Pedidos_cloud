package cl.duoc.pedidos360.bff.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import cl.duoc.pedidos360.bff.config.SecurityConfig;

import cl.duoc.pedidos360.bff.dto.OrderItemDTO;
import cl.duoc.pedidos360.bff.dto.OrderItemRequestDTO;
import cl.duoc.pedidos360.bff.dto.OrderRequestDTO;
import cl.duoc.pedidos360.bff.dto.OrderResponseDTO;
import cl.duoc.pedidos360.bff.dto.OrderStatusUpdateDTO;
import cl.duoc.pedidos360.bff.service.OrderProxyService;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class OrderControllerTest {

    private static final String AUTHORIZATION = "Bearer orders-test.token-unchanged";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderProxyService orderProxyService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, String role) {
        String token = AUTHORIZATION.substring("Bearer ".length());
        when(jwtDecoder.decode(token)).thenReturn(Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("user-123")
                .claim("roles", List.of(role))
                .build());
        return request.header(HttpHeaders.AUTHORIZATION, AUTHORIZATION);
    }

    private OrderResponseDTO sampleOrder(String status) {
        OrderItemDTO item = new OrderItemDTO(10L, "Teclado", 2, BigDecimal.valueOf(50), BigDecimal.valueOf(100));
        return new OrderResponseDTO(1L, "customer-1", "Ana Torres", status, BigDecimal.valueOf(100),
                null, null, null, null, List.of(item));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Operator", "Customer"})
    void listarReenviaAuthorizationYDevuelvePedidos(String role) throws Exception {
        when(orderProxyService.list(AUTHORIZATION)).thenReturn(List.of(sampleOrder("CREADO")));

        mockMvc.perform(authenticated(get("/api/orders"), role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Ana Torres"));
        verify(orderProxyService).list(AUTHORIZATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Operator", "Customer"})
    void obtenerReenviaIdYAuthorization(String role) throws Exception {
        when(orderProxyService.getById(1L, AUTHORIZATION)).thenReturn(sampleOrder("CREADO"));

        mockMvc.perform(authenticated(get("/api/orders/1"), role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].subtotal").value(100));
        verify(orderProxyService).getById(1L, AUTHORIZATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Operator", "Customer"})
    void crearReenviaItemsYAuthorization(String role) throws Exception {
        OrderRequestDTO body = new OrderRequestDTO(List.of(new OrderItemRequestDTO(10L, 2)));
        when(orderProxyService.create(body, AUTHORIZATION)).thenReturn(sampleOrder("CREADO"));

        mockMvc.perform(authenticated(post("/api/orders"), role)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customerName").value("Ana Torres"));
        verify(orderProxyService).create(body, AUTHORIZATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Operator"})
    void cambiarEstadoReenviaEstadoYAuthorization(String role) throws Exception {
        OrderStatusUpdateDTO body = new OrderStatusUpdateDTO("ACEPTADO");
        when(orderProxyService.changeStatus(1L, body, AUTHORIZATION)).thenReturn(sampleOrder("ACEPTADO"));

        mockMvc.perform(authenticated(patch("/api/orders/1/status"), role)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACEPTADO"));
        verify(orderProxyService).changeStatus(1L, body, AUTHORIZATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Operator", "Customer"})
    void cancelarReenviaAuthorizationYDevuelvePedido(String role) throws Exception {
        when(orderProxyService.cancel(1L, AUTHORIZATION)).thenReturn(sampleOrder("CANCELADO"));

        mockMvc.perform(authenticated(delete("/api/orders/1"), role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
        verify(orderProxyService).cancel(1L, AUTHORIZATION);
    }

    static Stream<Arguments> forbiddenRequests() {
        return Stream.of(
                Arguments.of("GET", "/api/orders", "", "SinPermisos"),
                Arguments.of("GET", "/api/orders/1", "", "SinPermisos"),
                Arguments.of("POST", "/api/orders", "{\"items\":[{\"productId\":10,\"quantity\":2}]}", "SinPermisos"),
                Arguments.of("PATCH", "/api/orders/1/status", "{\"status\":\"ACEPTADO\"}", "Customer"),
                Arguments.of("DELETE", "/api/orders/1", "", "SinPermisos"));
    }

    @ParameterizedTest
    @MethodSource("forbiddenRequests")
    void rechazaRolInsuficienteSinInvocarProxy(String method, String path, String body, String role) throws Exception {
        mockMvc.perform(authenticated(request(HttpMethod.valueOf(method), path), role)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(orderProxyService);
    }

    @ParameterizedTest
    @MethodSource("forbiddenRequests")
    void rechazaPeticionesSinToken(String method, String path, String body, String role) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderProxyService);
    }
}
