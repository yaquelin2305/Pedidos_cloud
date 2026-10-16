package cl.duoc.pedidos360.gateway.config;

import java.util.Arrays;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class ScopeValidator implements OAuth2TokenValidator<Jwt> {
    private final String requiredScope;

    ScopeValidator(String requiredScope) {
        this.requiredScope = requiredScope;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String scopes = token.getClaimAsString("scp");
        if (scopes != null && Arrays.asList(scopes.split("\\s+")).contains(requiredScope)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "El scope requerido no esta presente en el token", null));
    }
}
