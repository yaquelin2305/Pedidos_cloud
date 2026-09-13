package cl.duoc.pedidos360.bff.config;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import cl.duoc.pedidos360.bff.exception.RestAccessDeniedHandler;
import cl.duoc.pedidos360.bff.exception.RestAuthenticationEntryPoint;

/**
 * Seguridad del BFF. Toda ruta exige un JWT valido emitido por el IDaaS; la autorizacion
 * fina por rol se declara con {@code @PreAuthorize} en los controladores.
 *
 * <p>El {@code JwtDecoder} verifica firma (contra el JWK del issuer), issuer, vigencia y
 * audience. Un token invalido produce 401; un rol insuficiente, 403.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final String issuerUri;
    private final String audience;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${pedidos360.jwt.audience}") String audience,
            @Value("${pedidos360.cors.allowed-origins}") List<String> allowedOrigins) {
        this.issuerUri = issuerUri;
        this.audience = audience;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // El preflight CORS nunca trae credenciales; si se exige auth aqui, el
                        // navegador ve el preflight fallar y no llega a mandar la peticion real.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthConverter())))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)));
        return http.build();
    }

    /**
     * Origenes permitidos por variable de entorno: en local, el Vite dev server; en la nube,
     * el dominio real del frontend desplegado. Nunca "*" (baja la nota de R29 explicitamente).
     */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    @Profile("!test")
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        decoder.setJwtValidator(Pedidos360JwtValidators.forIssuerAndAudience(issuerUri, audience));
        return decoder;
    }
}
