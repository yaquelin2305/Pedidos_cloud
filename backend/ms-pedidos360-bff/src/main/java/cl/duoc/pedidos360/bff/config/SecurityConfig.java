package cl.duoc.pedidos360.bff.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

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

    public SecurityConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${pedidos360.jwt.audience}") String audience) {
        this.issuerUri = issuerUri;
        this.audience = audience;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthConverter())))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)));
        return http.build();
    }

    @Bean
    @Profile("!test")
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        decoder.setJwtValidator(Pedidos360JwtValidators.forIssuerAndAudience(issuerUri, audience));
        return decoder;
    }
}
