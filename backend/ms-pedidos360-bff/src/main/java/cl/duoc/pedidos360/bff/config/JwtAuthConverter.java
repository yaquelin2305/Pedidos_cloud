package cl.duoc.pedidos360.bff.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Traduce los claims de Azure Entra a authorities de Spring Security: el claim {@code roles}
 * se mapea a {@code ROLE_<nombre>} y el claim {@code scp} a {@code SCOPE_<valor>}.
 */
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLES_CLAIM = "roles";
    private static final String SCOPE_CLAIM = "scp";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.addAll(extractRoles(jwt));
        authorities.addAll(extractScopes(jwt));
        return new JwtAuthenticationToken(jwt, authorities);
    }

    private List<GrantedAuthority> extractRoles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    private List<GrantedAuthority> extractScopes(Jwt jwt) {
        String scope = jwt.getClaimAsString(SCOPE_CLAIM);
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return List.of(scope.split(" ")).stream()
                .filter(value -> !value.isBlank())
                .map(value -> (GrantedAuthority) new SimpleGrantedAuthority("SCOPE_" + value))
                .toList();
    }
}
