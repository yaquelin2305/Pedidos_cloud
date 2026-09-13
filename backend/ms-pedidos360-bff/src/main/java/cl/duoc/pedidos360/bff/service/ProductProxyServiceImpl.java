package cl.duoc.pedidos360.bff.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.bff.client.CatalogClient;
import cl.duoc.pedidos360.bff.dto.ProductRequestDTO;
import cl.duoc.pedidos360.bff.dto.ProductResponseDTO;
import cl.duoc.pedidos360.bff.dto.StockUpdateDTO;

@Service
@RequiredArgsConstructor
public class ProductProxyServiceImpl implements ProductProxyService {

    private final CatalogClient catalogClient;

    @Override
    public List<ProductResponseDTO> list(String authorization) {
        // catalog pagina la respuesta; el frontend espera una lista plana, asi que el BFF
        // aplana aqui en vez de trasladar el envelope de paginacion.
        return catalogClient.list(authorization).content();
    }

    @Override
    public ProductResponseDTO getById(Long id, String authorization) {
        return catalogClient.getById(id, authorization);
    }

    @Override
    public ProductResponseDTO create(ProductRequestDTO request, String authorization) {
        return catalogClient.create(request, authorization);
    }

    @Override
    public ProductResponseDTO update(Long id, ProductRequestDTO request, String authorization) {
        return catalogClient.update(id, request, authorization);
    }

    @Override
    public void deactivate(Long id, String authorization) {
        catalogClient.deactivate(id, authorization);
    }

    @Override
    public ProductResponseDTO updateStock(Long id, StockUpdateDTO update, String authorization) {
        return catalogClient.updateStock(id, update, authorization);
    }
}
