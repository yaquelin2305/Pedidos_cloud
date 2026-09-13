package cl.duoc.pedidos360.catalog.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.catalog.dto.ProductRequestDTO;
import cl.duoc.pedidos360.catalog.dto.ProductResponseDTO;
import cl.duoc.pedidos360.catalog.dto.StockAdjustmentDTO;
import cl.duoc.pedidos360.catalog.dto.StockUpdateDTO;
import cl.duoc.pedidos360.catalog.service.ProductService;

/**
 * Expone /api/catalog/products. Solo orquesta: la logica de negocio vive en ProductService.
 */
@RestController
@RequestMapping("/api/catalog/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operator', 'Customer')")
    public ResponseEntity<Page<ProductResponseDTO>> list(Pageable pageable) {
        return ResponseEntity.ok(productService.list(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operator', 'Customer')")
    public ResponseEntity<ProductResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<ProductResponseDTO> create(@Valid @RequestBody ProductRequestDTO request) {
        ProductResponseDTO created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<ProductResponseDTO> update(@PathVariable Long id,
            @Valid @RequestBody ProductRequestDTO request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        productService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint de integracion interna: lo invoca ms-pedidos360-orders al aceptar un pedido,
     * no el cliente final. Exige igualmente un JWT valido; no se restringe a un rol especifico
     * porque el llamador es otro microservicio y el mecanismo de autenticacion service-to-service
     * (client credentials u otro) se define junto con ms-pedidos360-orders.
     */
    @PatchMapping("/{id}/stock/decrease")
    public ResponseEntity<ProductResponseDTO> decreaseStock(@PathVariable Long id,
            @Valid @RequestBody StockAdjustmentDTO adjustment) {
        if (adjustment.productId() != null && !adjustment.productId().equals(id)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(productService.decreaseStock(id, adjustment.quantity()));
    }

    /**
     * Ajuste manual de stock desde la tabla del front (Admin/Operator), distinto del descuento
     * interno de {@code /stock/decrease}: aqui el body trae el valor absoluto final, no una
     * cantidad a restar.
     */
    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('Admin', 'Operator')")
    public ResponseEntity<ProductResponseDTO> updateStock(@PathVariable Long id,
            @Valid @RequestBody StockUpdateDTO update) {
        return ResponseEntity.ok(productService.updateStock(id, update.stock()));
    }
}
