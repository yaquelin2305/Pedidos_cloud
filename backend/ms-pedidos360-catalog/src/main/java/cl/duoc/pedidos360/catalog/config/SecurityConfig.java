package cl.duoc.pedidos360.catalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import cl.duoc.pedidos360.catalog.exception.RestAccessDeniedHandler;
import cl.duoc.pedidos360.catalog.exception.RestAuthenticationEntryPoint;

/**
 * Servicio de recursos OAuth2 sin sesion. El {@code JwtDecoder} autoconfigurado por Spring Boot
 * ya verifica firma (JWK del issuer), issuer, vigencia y audience a partir de
 * spring.security.oauth2.resourceserver.jwt.issuer-uri y .audiences; la autorizacion fina por
 * rol se declara con {@code @PreAuthorize} en el controlador. Swagger queda publico para poder
 * documentar la API sin exponer datos.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthConverter())))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)));
        return http.build();
    }
}
