package cl.duoc.pedidos360.mqadmin.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import cl.duoc.pedidos360.mqadmin.exception.RestAccessDeniedHandler;
import cl.duoc.pedidos360.mqadmin.exception.RestAuthenticationEntryPoint;

/**
 * Servicio de recursos OAuth2 sin sesion. El {@code JwtDecoder} autoconfigurado por Spring Boot
 * ya verifica firma (JWK del issuer), issuer, vigencia y audience a partir de
 * spring.security.oauth2.resourceserver.jwt.issuer-uri y .audiences. El controlador exige el rol
 * Admin con {@code @PreAuthorize}: administrar el broker no es una operacion de usuario final.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
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
