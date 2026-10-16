package cl.duoc.pedidos360.gateway.config;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import cl.duoc.pedidos360.gateway.support.RsaKeys;
import cl.duoc.pedidos360.gateway.support.TestJwt;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(GatewaySecurityTest.TestConfig.class)
class GatewaySecurityTest {
    private static final String ISSUER = "https://test-issuer.pedidos360.local";
    private static final String AUDIENCE = "api://pedidos360-test";
    private static final String ORIGIN = "http://localhost:5173";
    private static final RsaKeys KEYS = new RsaKeys();
    private static final TestJwt TOKENS = new TestJwt(KEYS, ISSUER, AUDIENCE);
    private static final MockWebServer BFF = startBff();

    @Autowired
    private WebTestClient client;

    private static MockWebServer startBff() {
        var server = new MockWebServer();
        try {
            server.start();
            return server;
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("BFF_BASE_URL", () -> BFF.url("/").toString());
    }

    @AfterAll
    static void stopBff() throws IOException {
        BFF.shutdown();
    }

    private void unauthorized(String token) {
        int requests = BFF.getRequestCount();
        var request = client.get().uri("/api/orders");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        request.exchange().expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.message").isEqualTo("Token ausente, expirado o invalido")
                .jsonPath("$.timestamp").exists();
        assertThat(BFF.getRequestCount()).isEqualTo(requests);
    }

    @Test
    void rejectsMissingToken() {
        unauthorized(null);
    }

    @Test
    void rejectsInvalidSignature() {
        unauthorized(new TestJwt(new RsaKeys(), ISSUER, AUDIENCE).valid(List.of("Admin")));
    }

    @Test
    void rejectsExpiredToken() {
        unauthorized(TOKENS.expired(List.of("Admin")));
    }

    @Test
    void rejectsWrongIssuer() {
        unauthorized(TOKENS.wrongIssuer(List.of("Admin")));
    }

    @Test
    void rejectsWrongAudience() {
        unauthorized(TOKENS.wrongAudience(List.of("Admin")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"orders.read", "access_as_user_extra"})
    void rejectsMissingRequiredScope(String scope) {
        unauthorized(TOKENS.withScope(scope));
    }

    static Stream<Arguments> routeRoles() {
        String[][] routes = {
            {"GET", "/api/orders", "all"}, {"GET", "/api/orders/1", "all"},
            {"POST", "/api/orders", "all"}, {"DELETE", "/api/orders/1", "all"},
            {"PATCH", "/api/orders/1/status", "staff"},
            {"GET", "/api/catalog/products", "all"}, {"GET", "/api/catalog/products/1", "all"},
            {"POST", "/api/catalog/products", "admin"}, {"PUT", "/api/catalog/products/1", "admin"},
            {"DELETE", "/api/catalog/products/1", "admin"},
            {"PATCH", "/api/catalog/products/1/stock", "staff"}
        };
        return Stream.of(routes).flatMap(route -> Stream.of("Admin", "Operator", "Customer")
                .map(role -> Arguments.of(route[0], route[1], role,
                        role.equals("Admin") || route[2].equals("all")
                                || (route[2].equals("staff") && role.equals("Operator")))));
    }

    @ParameterizedTest
    @MethodSource("routeRoles")
    void enforcesRolesAndPreservesRequest(String method, String path, String role, boolean allowed) throws Exception {
        String authorization = "Bearer " + TOKENS.valid(List.of(role));
        int requests = BFF.getRequestCount();
        if (allowed) {
            BFF.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"ok\":true}"));
        }
        var response = client.method(HttpMethod.valueOf(method)).uri(path)
                .header("Authorization", authorization).header("Origin", ORIGIN).exchange();
        response.expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGIN);
        if (allowed) {
            response.expectStatus().isOk().expectBody().jsonPath("$.ok").isEqualTo(true);
            var received = BFF.takeRequest(2, TimeUnit.SECONDS);
            assertThat(received).isNotNull();
            assertThat(received.getPath()).isEqualTo(path);
            assertThat(received.getMethod()).isEqualTo(method);
            assertThat(received.getHeader("Authorization")).isEqualTo(authorization);
        } else {
            response.expectStatus().isForbidden().expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody().jsonPath("$.status").isEqualTo(403)
                    .jsonPath("$.error").isEqualTo("Forbidden")
                    .jsonPath("$.timestamp").exists()
                    .jsonPath("$.message").isEqualTo("El rol del usuario no autoriza esta operacion");
            assertThat(BFF.getRequestCount()).isEqualTo(requests);
        }
    }

    @Test
    void allowsMeWithoutRole() throws Exception {
        BFF.enqueue(new MockResponse().setBody("{}"));
        client.get().uri("/me").header("Authorization", "Bearer " + TOKENS.valid(List.of()))
                .exchange().expectStatus().isOk();
        assertThat(BFF.takeRequest(2, TimeUnit.SECONDS).getPath()).isEqualTo("/me");
    }

    @Test
    void allowsPreflightBeforeAuthentication() {
        int requests = BFF.getRequestCount();
        client.options().uri("/api/orders").header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGIN)
                .expectHeader().valueEquals("Access-Control-Max-Age", "3600")
                .expectHeader().doesNotExist("Access-Control-Allow-Credentials");
        assertThat(BFF.getRequestCount()).isEqualTo(requests);
    }

    @Test
    void rejectsUntrustedPreflightOrigin() {
        int requests = BFF.getRequestCount();
        client.options().uri("/api/orders").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET")
                .exchange().expectStatus().isForbidden()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin");
        assertThat(BFF.getRequestCount()).isEqualTo(requests);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/unknown", "/api/orders/1/unknown", "/api/catalog/unknown"})
    void rejectsUnmappedPath(String path) {
        int requests = BFF.getRequestCount();
        client.get().uri(path).header("Authorization", "Bearer " + TOKENS.valid(List.of("Admin")))
                .exchange().expectStatus().isForbidden();
        assertThat(BFF.getRequestCount()).isEqualTo(requests);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ReactiveJwtDecoder jwtDecoder() {
            var decoder = NimbusReactiveJwtDecoder.withPublicKey(KEYS.publicKey).build();
            decoder.setJwtValidator(Pedidos360JwtValidators.forIssuerAudienceAndScope(ISSUER, AUDIENCE, "access_as_user"));
            return decoder;
        }
    }
}
