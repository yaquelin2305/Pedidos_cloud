package cl.duoc.pedidos360.gateway.config;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import cl.duoc.pedidos360.gateway.exception.RestAccessDeniedHandler;
import cl.duoc.pedidos360.gateway.exception.RestAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityWebFilterChain filterChain(ServerHttpSecurity http, ObjectMapper mapper,
            @Value("${pedidos360.cors.allowed-origins}") List<String> allowedOrigins) {
        var entryPoint = new RestAuthenticationEntryPoint(mapper);
        var deniedHandler = new RestAccessDeniedHandler(mapper);
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins.stream().map(String::trim).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .cors(config -> config.configurationSource(source))
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/me").authenticated()
                        .pathMatchers(HttpMethod.GET, "/api/orders", "/api/orders/{id}",
                                "/api/catalog/products", "/api/catalog/products/{id}")
                            .hasAnyRole("Admin", "Operator", "Customer")
                        .pathMatchers(HttpMethod.POST, "/api/orders").hasAnyRole("Admin", "Operator", "Customer")
                        .pathMatchers(HttpMethod.DELETE, "/api/orders/{id}").hasAnyRole("Admin", "Operator", "Customer")
                        .pathMatchers(HttpMethod.PATCH, "/api/orders/{id}/status", "/api/catalog/products/{id}/stock")
                            .hasAnyRole("Admin", "Operator")
                        .pathMatchers(HttpMethod.POST, "/api/catalog/products").hasRole("Admin")
                        .pathMatchers(HttpMethod.PUT, "/api/catalog/products/{id}").hasRole("Admin")
                        .pathMatchers(HttpMethod.DELETE, "/api/catalog/products/{id}").hasRole("Admin")
                        .pathMatchers(HttpMethod.GET, "/api/mq/**").hasRole("Admin")
                        .pathMatchers(HttpMethod.POST, "/api/mq/**").hasRole("Admin")
                        .pathMatchers(HttpMethod.DELETE, "/api/mq/**").hasRole("Admin")
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new ReactiveJwtAuthenticationConverterAdapter(new JwtAuthConverter()))))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .build();
    }

    @Bean
    @Profile("!test")
    ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${pedidos360.jwt.audience}") String audience,
            @Value("${pedidos360.jwt.required-scope}") String scope) {
        var decoder = NimbusReactiveJwtDecoder.withIssuerLocation(issuer).build();
        decoder.setJwtValidator(Pedidos360JwtValidators.forIssuerAudienceAndScope(issuer, audience, scope));
        return decoder;
    }
}
