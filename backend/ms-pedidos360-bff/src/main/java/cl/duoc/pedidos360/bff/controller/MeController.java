package cl.duoc.pedidos360.bff.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.bff.dto.AuthenticatedUserResponse;

/**
 * Expone los claims del token ya validado. Permite verificar la cadena de seguridad de
 * extremo a extremo sin depender de los microservicios de dominio.
 */
@RestController
@RequestMapping("/me")
public class MeController {

    @GetMapping
    public AuthenticatedUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        String scope = jwt.getClaimAsString("scp");
        List<String> scopes = (scope == null || scope.isBlank())
                ? List.of()
                : List.of(scope.split(" "));
        return new AuthenticatedUserResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("name"),
                roles == null ? List.of() : roles,
                scopes);
    }
}
