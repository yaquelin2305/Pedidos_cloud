package cl.duoc.pedidos360.gateway.config;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

final class Pedidos360JwtValidators {
    private Pedidos360JwtValidators() {
    }

    static OAuth2TokenValidator<Jwt> forIssuerAudienceAndScope(String issuer, String audience, String scope) {
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceValidator(audience), new ScopeValidator(scope));
    }
}
