package cl.duoc.pedidos360.audit.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.duoc.pedidos360.audit.config.SecurityConfig;
import cl.duoc.pedidos360.audit.dto.AuditEntryResponseDTO;
import cl.duoc.pedidos360.audit.entity.AggregateType;
import cl.duoc.pedidos360.audit.service.AuditService;

/**
 * Prueba el controller con MockMvc y el servicio mockeado: autorizacion (solo Admin) y validacion de
 * la query. jwt() coloca las authorities directamente, sin consultar al issuer real.
 */
@WebMvcTest(AuditController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-issuer.pedidos360.local")
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    @TestConfiguration
    static class JwtDecoderTestConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("no se usa: jwt() reemplaza la decodificacion en el test");
            };
        }
    }

    private static SimpleGrantedAuthority role(String role) {
        return new SimpleGrantedAuthority("ROLE_" + role);
    }

    @Test
    void adminConsultaLaBitacoraConFiltros() throws Exception {
        AuditEntryResponseDTO entry = new AuditEntryResponseDTO(1L, UUID.randomUUID(), "pedidos360.orders.events", 0,
                4L, "ORDER_CREATED", AggregateType.ORDER, 12L, "user-1", "Ana", "Pedido 12 creado en CREADO por Ana",
                "{\"orderId\":12}", Instant.now(), Instant.now());
        when(auditService.search(AggregateType.ORDER, 12L, 0, 20))
                .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/audit/entries").param("aggregateType", "ORDER").param("aggregateId", "12")
                        .with(jwt().authorities(role("Admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].summary").value("Pedido 12 creado en CREADO por Ana"))
                .andExpect(jsonPath("$.content[0].payload.orderId").value(12));
    }

    @Test
    void adminConsultaSinFiltrosConPaginacionPorDefecto() throws Exception {
        when(auditService.search(isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/audit/entries").with(jwt().authorities(role("Admin"))))
                .andExpect(status().isOk());

        verify(auditService).search(isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    void operatorNoPuedeConsultarLaBitacora() throws Exception {
        mockMvc.perform(get("/api/audit/entries").with(jwt().authorities(role("Operator"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(auditService);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/audit/entries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aggregateTypeInvalidoResponde400ConLosValoresPermitidos() throws Exception {
        mockMvc.perform(get("/api/audit/entries").param("aggregateType", "INVOICE")
                        .with(jwt().authorities(role("Admin"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("ORDER, PRODUCT")));

        verifyNoInteractions(auditService);
    }

    @Test
    void sizeMayorA100Responde400() throws Exception {
        mockMvc.perform(get("/api/audit/entries").param("size", "101")
                        .with(jwt().authorities(role("Admin"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("size")));

        verifyNoInteractions(auditService);
    }
}
