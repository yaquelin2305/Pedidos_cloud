package cl.duoc.pedidos360.report.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import cl.duoc.pedidos360.report.config.SecurityConfig;
import cl.duoc.pedidos360.report.dto.ReportSummaryDTO;
import cl.duoc.pedidos360.report.service.ReportQueryService;

@WebMvcTest(controllers = ReportController.class, properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-issuer.pedidos360.local",
        "spring.security.oauth2.resourceserver.jwt.audiences=test-api"
})
@Import(SecurityConfig.class)
class ReportControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ReportQueryService service;
    @MockitoBean private JwtDecoder decoder;

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Operator"})
    void allowedRolesCanReadEveryEndpoint(String role) throws Exception {
        var token = jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
        when(service.summary()).thenReturn(new ReportSummaryDTO(Map.of(), BigDecimal.ZERO, 0, 0, null, null));
        when(service.topProducts(5)).thenReturn(List.of());
        when(service.lowStock()).thenReturn(List.of());
        mvc.perform(get("/api/reports/summary").with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedSales").value(0));
        mvc.perform(get("/api/reports/top-products").with(token)).andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(get("/api/reports/low-stock").with(token)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"summary", "top-products", "low-stock"})
    void rejectsAnonymousAndCustomer(String path) throws Exception {
        mvc.perform(get("/api/reports/" + path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports/" + path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "21", "invalid"})
    void rejectsInvalidLimit(String limit) throws Exception {
        mvc.perform(get("/api/reports/top-products").param("limit", limit)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        when(decoder.decode("invalid")).thenThrow(new org.springframework.security.oauth2.jwt.BadJwtException("invalido"));
        mvc.perform(get("/api/reports/summary").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
