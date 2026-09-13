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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.bff.dto.ProductRequestDTO;
import cl.duoc.pedidos360.bff.dto.ProductResponseDTO;
import cl.duoc.pedidos360.bff.dto.StockUpdateDTO;
import cl.duoc.pedidos360.bff.service.ProductProxyService;

/**
 * Enruta /api/catalog/products hacia ms-pedidos360-catalog.
 */
@RestController
@RequestMapping("/api/catalog/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductProxyService productProxyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operator', 'Customer')")
    public ResponseEntity<List<ProductResponseDTO>> list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ResponseEntity.ok(productProxyService.list(authorization));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operator', 'Customer')")
    public ResponseEntity<ProductResponseDTO> getById(@PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ResponseEntity.ok(productProxyService.getById(id, authorization));
    }

    @PostMapping
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<ProductResponseDTO> create(@Valid @RequestBody ProductRequestDTO request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productProxyService.create(request, authorization));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<ProductResponseDTO> update(@PathVariable Long id,
            @Valid @RequestBody ProductRequestDTO request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ResponseEntity.ok(productProxyService.update(id, request, authorization));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        productProxyService.deactivate(id, authorization);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('Admin', 'Operator')")
    public ResponseEntity<ProductResponseDTO> updateStock(@PathVariable Long id,
            @Valid @RequestBody StockUpdateDTO update,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ResponseEntity.ok(productProxyService.updateStock(id, update, authorization));
    }
}
