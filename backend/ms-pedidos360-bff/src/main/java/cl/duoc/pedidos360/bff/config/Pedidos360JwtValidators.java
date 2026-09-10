package cl.duoc.pedidos360.bff.config;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

/**
 * Cadena de validacion aplicada a cada JWT: issuer y vigencia (validadores por defecto de
 * Spring) mas la verificacion de audience propia del proyecto. La firma la comprueba el
 * {@code JwtDecoder} contra el JWK del IDaaS.
 */
final class Pedidos360JwtValidators {

    private Pedidos360JwtValidators() {
    }

    static OAuth2TokenValidator<Jwt> forIssuerAndAudience(String issuerUri, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AudienceValidator(audience));
    }
}
