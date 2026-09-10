package cl.duoc.pedidos360.catalog.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.catalog.dto.ProductRequestDTO;
import cl.duoc.pedidos360.catalog.dto.ProductResponseDTO;
import cl.duoc.pedidos360.catalog.entity.Product;
import cl.duoc.pedidos360.catalog.exception.InsufficientStockException;
import cl.duoc.pedidos360.catalog.exception.ProductNotFoundException;
import cl.duoc.pedidos360.catalog.repository.ProductRepository;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> list(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable).map(ProductResponseDTO::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDTO getById(Long id) {
        return ProductResponseDTO.from(findActiveOrThrow(id));
    }

    @Override
    @Transactional
    public ProductResponseDTO create(ProductRequestDTO request) {
        Product product = Product.builder()
                .sku(request.sku())
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .stock(request.stock())
                .category(request.category())
                .build();
        return ProductResponseDTO.from(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponseDTO update(Long id, ProductRequestDTO request) {
        Product product = findActiveOrThrow(id);
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setCategory(request.category());
        return ProductResponseDTO.from(productRepository.save(product));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        Product product = findActiveOrThrow(id);
        product.setActive(false);
        productRepository.save(product);
    }

    /**
     * Descuenta stock al aceptar un pedido en ms-pedidos360-orders. El bloqueo optimista
     * de {@code Product.version} hace que, si dos pedidos descuentan el mismo producto a
     * la vez, la segunda transaccion en confirmar falle con {@code OptimisticLockException}
     * en vez de sobreescribir el stock calculado por la primera.
     */
    @Override
    @Transactional
    public ProductResponseDTO decreaseStock(Long id, int quantity) {
        Product product = findActiveOrThrow(id);
        if (product.getStock() < quantity) {
            throw new InsufficientStockException(id, product.getStock(), quantity);
        }
        product.setStock(product.getStock() - quantity);
        return ProductResponseDTO.from(productRepository.save(product));
    }

    private Product findActiveOrThrow(Long id) {
        return productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
