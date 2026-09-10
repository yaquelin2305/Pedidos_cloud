package cl.duoc.pedidos360.catalog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.duoc.pedidos360.catalog.config.SecurityConfig;
import cl.duoc.pedidos360.catalog.dto.ProductRequestDTO;
import cl.duoc.pedidos360.catalog.dto.ProductResponseDTO;
import cl.duoc.pedidos360.catalog.service.ProductService;

/**
 * Prueba liviana del controller con MockMvc. jwt() simula un token ya decodificado y coloca
 * las authorities directamente en el contexto de seguridad: no golpea al issuer real. Verifica
 * que la ruta de escritura exige el rol Admin y que sin token responde 401.
 */
@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-issuer.pedidos360.local")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @org.springframework.boot.test.context.TestConfiguration
    static class JwtDecoderTestConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("no se usa: jwt() reemplaza la decodificacion en el test");
            };
        }
    }

    @Test
    void listarProductosRespondeOkConTokenValido() throws Exception {
        when(productService.list(any())).thenReturn(new PageImpl<>(java.util.List.of()));

        mockMvc.perform(get("/api/catalog/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer"))))
                .andExpect(status().isOk());
    }

    @Test
    void listarProductosSinTokenRespondeNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/catalog/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearProductoConRolAdminRespondeCreado() throws Exception {
        ProductRequestDTO request = new ProductRequestDTO("SKU-1", "Mouse", "Mouse optico",
                BigDecimal.valueOf(15), 30, "Perifericos");
        ProductResponseDTO response = new ProductResponseDTO(1L, "SKU-1", "Mouse", "Mouse optico",
                BigDecimal.valueOf(15), 30, "Perifericos", true, null, null);
        when(productService.create(eq(request))).thenReturn(response);

        mockMvc.perform(post("/api/catalog/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-1","name":"Mouse","description":"Mouse optico",
                                 "price":15,"stock":30,"category":"Perifericos"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void crearProductoConRolInsuficienteRespondeProhibido() throws Exception {
        mockMvc.perform(post("/api/catalog/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Customer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-1","name":"Mouse","description":"Mouse optico",
                                 "price":15,"stock":30,"category":"Perifericos"}
                                """))
                .andExpect(status().isForbidden());
    }
}
