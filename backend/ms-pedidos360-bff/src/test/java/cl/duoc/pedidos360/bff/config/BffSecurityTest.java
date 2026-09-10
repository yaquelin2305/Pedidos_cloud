package cl.duoc.pedidos360.bff.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.bff.support.RsaKeys;
import cl.duoc.pedidos360.bff.support.TestJwt;

/**
 * Cubre R18: el BFF valida firma, issuer, audience y vigencia del JWT, aplica autorizacion
 * por rol y responde 401 frente a 403 de forma coherente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BffSecurityTest.TestConfig.class)
class BffSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RsaKeys keys;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuer;

    @Value("${pedidos360.jwt.audience}")
    private String audience;

    private TestJwt tokens() {
        return new TestJwt(keys, issuer, audience);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void permiteAccesoConTokenValidoYExponeRolesYScopes() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", bearer(tokens().valid(List.of("Admin")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("user-123"))
                .andExpect(jsonPath("$.roles[0]").value("Admin"))
                .andExpect(jsonPath("$.scopes", Matchers.hasItem("orders.read")));
    }

    @Test
    void rechazaConIntentoSinToken() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rechazaTokenExpirado() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", bearer(tokens().expired(List.of("Admin")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaAudienceIncorrecto() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", bearer(tokens().wrongAudience(List.of("Admin")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaIssuerIncorrecto() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", bearer(tokens().wrongIssuer(List.of("Admin")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaFirmaDeOtraClave() throws Exception {
        TestJwt forjado = new TestJwt(new RsaKeys(), issuer, audience);
        mockMvc.perform(get("/me").header("Authorization", bearer(forjado.valid(List.of("Admin")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void devuelve403ConTokenValidoPeroRolInsuficiente() throws Exception {
        mockMvc.perform(get("/test/admin-only")
                        .header("Authorization", bearer(tokens().valid(List.of("Customer")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void permiteRutaProtegidaPorRolCuandoElRolCorresponde() throws Exception {
        mockMvc.perform(get("/test/admin-only")
                        .header("Authorization", bearer(tokens().valid(List.of("Admin")))))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        RsaKeys rsaKeys() {
            return new RsaKeys();
        }

        @Bean
        JwtDecoder jwtDecoder(RsaKeys keys,
                @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
                @Value("${pedidos360.jwt.audience}") String audience) {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey).build();
            decoder.setJwtValidator(Pedidos360JwtValidators.forIssuerAndAudience(issuer, audience));
            return decoder;
        }

        @RestController
        static class GuardedTestController {

            @GetMapping("/test/admin-only")
            @PreAuthorize("hasRole('Admin')")
            String adminOnly() {
                return "ok";
            }
        }
    }
}
