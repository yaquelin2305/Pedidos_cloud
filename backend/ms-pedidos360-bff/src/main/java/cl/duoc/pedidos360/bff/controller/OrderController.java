package cl.duoc.pedidos360.bff.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.bff.dto.OrderRequestDTO;
import cl.duoc.pedidos360.bff.dto.OrderResponseDTO;
import cl.duoc.pedidos360.bff.dto.OrderStatusUpdateDTO;
import cl.duoc.pedidos360.bff.service.OrderProxyService;

/**
 * Enruta /api/orders hacia ms-pedidos360-orders. Los roles declarados aqui son los mismos que
 * exige el microservicio: evita el viaje redondo para una peticion que ya se sabe rechazada,
 * pero orders vuelve a validar el token de forma independiente (doble validacion).
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderProxyService orderProxyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operator', 'Customer')")
    public ResponseEntity<List<OrderResponseDTO>> list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ResponseEntity.ok(orderProxyService.list(authorization));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operator', 'Customer')")
    public ResponseEntity<OrderResponseDTO> getById(@PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ResponseEntity.ok(orderProxyService.getById(id, authorization));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operator', 'Customer')")
    public ResponseEntity<OrderResponseDTO> create(@Valid @RequestBody OrderRequestDTO request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderProxyService.create(request, authorization));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('Operator', 'Admin')")
    public ResponseEntity<OrderResponseDTO> changeStatus(@PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateDTO update,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ResponseEntity.ok(orderProxyService.changeStatus(id, update, authorization));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operator', 'Customer')")
    public ResponseEntity<OrderResponseDTO> cancel(@PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ResponseEntity.ok(orderProxyService.cancel(id, authorization));
    }
}
