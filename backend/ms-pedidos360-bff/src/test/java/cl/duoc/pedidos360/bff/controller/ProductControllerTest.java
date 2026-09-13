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

import cl.duoc.pedidos360.bff.dto.ProductRequestDTO;
import cl.duoc.pedidos360.bff.dto.ProductResponseDTO;
import cl.duoc.pedidos360.bff.dto.StockUpdateDTO;
import cl.duoc.pedidos360.bff.service.ProductProxyService;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ProductControllerTest {

    private static final String AUTHORIZATION = "Bearer catalog-test.token-unchanged";
    private static final String PRODUCT_JSON = """
            {"sku":"SKU-1","name":"Mouse","description":"Mouse optico","price":15,"stock":30,"category":"Perifericos"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductProxyService productProxyService;

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

    private ProductRequestDTO sampleRequest() {
        return new ProductRequestDTO("SKU-1", "Mouse", "Mouse optico", BigDecimal.valueOf(15), 30, "Perifericos");
    }

    private ProductResponseDTO sampleProduct(int stock) {
        return new ProductResponseDTO(1L, "SKU-1", "Mouse", "Mouse optico", BigDecimal.valueOf(15),
                stock, "Perifericos", true, null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Operator", "Customer"})
    void listarReenviaAuthorizationYDevuelveLista(String role) throws Exception {
        when(productProxyService.list(AUTHORIZATION)).thenReturn(List.of(sampleProduct(30)));

        mockMvc.perform(authenticated(get("/api/catalog/products"), role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Mouse"));
        verify(productProxyService).list(AUTHORIZATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Operator", "Customer"})
    void obtenerReenviaIdYAuthorization(String role) throws Exception {
        when(productProxyService.getById(1L, AUTHORIZATION)).thenReturn(sampleProduct(30));

        mockMvc.perform(authenticated(get("/api/catalog/products/1"), role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(30));
        verify(productProxyService).getById(1L, AUTHORIZATION);
    }

    @org.junit.jupiter.api.Test
    void crearReenviaProductoYAuthorization() throws Exception {
        ProductRequestDTO body = sampleRequest();
        when(productProxyService.create(body, AUTHORIZATION)).thenReturn(sampleProduct(30));

        mockMvc.perform(authenticated(post("/api/catalog/products"), "Admin")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
        verify(productProxyService).create(body, AUTHORIZATION);
    }

    @org.junit.jupiter.api.Test
    void actualizarReenviaProductoYAuthorization() throws Exception {
        ProductRequestDTO body = sampleRequest();
        when(productProxyService.update(1L, body, AUTHORIZATION)).thenReturn(sampleProduct(30));

        mockMvc.perform(authenticated(put("/api/catalog/products/1"), "Admin")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mouse"));
        verify(productProxyService).update(1L, body, AUTHORIZATION);
    }

    @org.junit.jupiter.api.Test
    void desactivarReenviaAuthorizationYDevuelve204() throws Exception {
        mockMvc.perform(authenticated(delete("/api/catalog/products/1"), "Admin"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(productProxyService).deactivate(1L, AUTHORIZATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Operator"})
    void ajustarStockReenviaCantidadYAuthorization(String role) throws Exception {
        StockUpdateDTO body = new StockUpdateDTO(50);
        when(productProxyService.updateStock(1L, body, AUTHORIZATION)).thenReturn(sampleProduct(50));

        mockMvc.perform(authenticated(patch("/api/catalog/products/1/stock"), role)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(50));
        verify(productProxyService).updateStock(1L, body, AUTHORIZATION);
    }

    static Stream<Arguments> forbiddenRequests() {
        return Stream.of(
                Arguments.of("GET", "/api/catalog/products", "", "SinPermisos"),
                Arguments.of("GET", "/api/catalog/products/1", "", "SinPermisos"),
                Arguments.of("POST", "/api/catalog/products", PRODUCT_JSON, "Operator"),
                Arguments.of("POST", "/api/catalog/products", PRODUCT_JSON, "Customer"),
                Arguments.of("PUT", "/api/catalog/products/1", PRODUCT_JSON, "Operator"),
                Arguments.of("PUT", "/api/catalog/products/1", PRODUCT_JSON, "Customer"),
                Arguments.of("DELETE", "/api/catalog/products/1", "", "Operator"),
                Arguments.of("DELETE", "/api/catalog/products/1", "", "Customer"),
                Arguments.of("PATCH", "/api/catalog/products/1/stock", "{\"stock\":50}", "Customer"));
    }

    @ParameterizedTest
    @MethodSource("forbiddenRequests")
    void rechazaRolInsuficienteSinInvocarProxy(String method, String path, String body, String role) throws Exception {
        mockMvc.perform(authenticated(request(HttpMethod.valueOf(method), path), role)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(productProxyService);
    }
}
