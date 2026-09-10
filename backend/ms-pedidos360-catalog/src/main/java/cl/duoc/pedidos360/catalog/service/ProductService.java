package cl.duoc.pedidos360.catalog.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import cl.duoc.pedidos360.catalog.dto.ProductRequestDTO;
import cl.duoc.pedidos360.catalog.dto.ProductResponseDTO;

public interface ProductService {

    Page<ProductResponseDTO> list(Pageable pageable);

    ProductResponseDTO getById(Long id);

    ProductResponseDTO create(ProductRequestDTO request);

    ProductResponseDTO update(Long id, ProductRequestDTO request);

    void deactivate(Long id);

    ProductResponseDTO decreaseStock(Long id, int quantity);
}
