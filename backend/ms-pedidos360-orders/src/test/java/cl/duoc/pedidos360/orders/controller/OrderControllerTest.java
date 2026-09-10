package cl.duoc.pedidos360.orders.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.duoc.pedidos360.orders.config.SecurityConfig;
import cl.duoc.pedidos360.orders.dto.OrderItemDTO;
import cl.duoc.pedidos360.orders.dto.OrderResponseDTO;
import cl.duoc.pedidos360.orders.entity.OrderStatus;
import cl.duoc.pedidos360.orders.service.OrderService;

/**
 * Prueba liviana del controller con MockMvc, mismo patron que ProductControllerTest en catalog:
 * jwt() simula un token ya decodificado y coloca las authorities directamente en el contexto de
 * seguridad, sin golpear al issuer real.
 */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-issuer.pedidos360.local")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @org.springframework.boot.test.context.TestConfiguration
    static class JwtDecoderTestConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("no se usa: jwt() reemplaza la decodificacion en el test");
            };
        }
    }

    private OrderResponseDTO sampleOrder() {
        OrderItemDTO item = new OrderItemDTO(10L, "Teclado", 2, BigDecimal.valueOf(50), BigDecimal.valueOf(100));
        return new OrderResponseDTO(1L, "customer-1", OrderStatus.CREADO, BigDecimal.valueOf(100),
                null, null, null, null, List.of(item));
    }

    @Test
    void listarPedidosRespondeOkConTokenValido() throws Exception {
        when(orderService.list(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer"))))
                .andExpect(status().isOk());
    }

    @Test
    void listarPedidosSinTokenRespondeNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearPedidoConRolClienteRespondeCreado() throws Exception {
        when(orderService.create(any(), any())).thenReturn(sampleOrder());

        mockMvc.perform(post("/api/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":10,"quantity":2}]}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void cambiarEstadoConRolClienteRespondeProhibido() throws Exception {
        mockMvc.perform(patch("/api/orders/1/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACEPTADO"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void cambiarEstadoConRolOperadorRespondeOk() throws Exception {
        when(orderService.changeStatus(eq(1L), eq(OrderStatus.ACEPTADO), any())).thenReturn(sampleOrder());

        mockMvc.perform(patch("/api/orders/1/status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operator")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACEPTADO"}
                                """))
                .andExpect(status().isOk());
    }
}
