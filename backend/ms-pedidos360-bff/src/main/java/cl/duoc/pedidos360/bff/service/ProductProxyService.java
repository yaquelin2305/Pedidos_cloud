package cl.duoc.pedidos360.bff.service;

import java.util.List;

import cl.duoc.pedidos360.bff.dto.ProductRequestDTO;
import cl.duoc.pedidos360.bff.dto.ProductResponseDTO;
import cl.duoc.pedidos360.bff.dto.StockUpdateDTO;

public interface ProductProxyService {

    List<ProductResponseDTO> list(String authorization);

    ProductResponseDTO getById(Long id, String authorization);

    ProductResponseDTO create(ProductRequestDTO request, String authorization);

    ProductResponseDTO update(Long id, ProductRequestDTO request, String authorization);

    void deactivate(Long id, String authorization);

    ProductResponseDTO updateStock(Long id, StockUpdateDTO update, String authorization);
}
