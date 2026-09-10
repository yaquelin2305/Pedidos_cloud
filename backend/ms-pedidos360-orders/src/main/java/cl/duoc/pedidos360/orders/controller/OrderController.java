package cl.duoc.pedidos360.orders.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.orders.dto.OrderRequestDTO;
import cl.duoc.pedidos360.orders.dto.OrderResponseDTO;
import cl.duoc.pedidos360.orders.dto.OrderStatusUpdateDTO;
import cl.duoc.pedidos360.orders.service.OrderRequester;
import cl.duoc.pedidos360.orders.service.OrderService;

/**
 * Expone /api/orders. La identidad y el rol se extraen del JWT (no de parametros de la
 * peticion) y se pasan al Service como OrderRequester: el ownership y las reglas por rol se
 * validan en el Service, aqui solo se orquesta.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Gestion de pedidos y su ciclo de estados")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operator', 'Customer')")
    public ResponseEntity<List<OrderResponseDTO>> list(JwtAuthenticationToken auth) {
        return ResponseEntity.ok(orderService.list(requesterOf(auth)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operator', 'Customer')")
    public ResponseEntity<OrderResponseDTO> getById(@PathVariable Long id, JwtAuthenticationToken auth) {
        return ResponseEntity.ok(orderService.getById(id, requesterOf(auth)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('Customer', 'Operator')")
    public ResponseEntity<OrderResponseDTO> create(@Valid @RequestBody OrderRequestDTO request,
            JwtAuthenticationToken auth) {
        OrderResponseDTO created = orderService.create(request, requesterOf(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('Operator', 'Admin')")
    public ResponseEntity<OrderResponseDTO> changeStatus(@PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateDTO update, JwtAuthenticationToken auth) {
        return ResponseEntity.ok(orderService.changeStatus(id, update.status(), requesterOf(auth)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('Customer', 'Operator', 'Admin')")
    public ResponseEntity<OrderResponseDTO> cancel(@PathVariable Long id, JwtAuthenticationToken auth) {
        return ResponseEntity.ok(orderService.cancel(id, requesterOf(auth)));
    }

    private OrderRequester requesterOf(JwtAuthenticationToken auth) {
        Jwt jwt = auth.getToken();
        return new OrderRequester(jwt.getSubject(), isPrivileged(auth));
    }

    private boolean isPrivileged(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if (name.equals("ROLE_Operator") || name.equals("ROLE_Admin")) {
                return true;
            }
        }
        return false;
    }
}
