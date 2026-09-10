package cl.duoc.pedidos360.bff.dto;

import java.util.List;

/**
 * Proyeccion de los claims relevantes del usuario autenticado.
 */
public record AuthenticatedUserResponse(
        String subject,
        String name,
        List<String> roles,
        List<String> scopes) {
}
