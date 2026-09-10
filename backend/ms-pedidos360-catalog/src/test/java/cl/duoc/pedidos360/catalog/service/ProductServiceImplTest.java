package cl.duoc.pedidos360.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.duoc.pedidos360.catalog.dto.ProductRequestDTO;
import cl.duoc.pedidos360.catalog.dto.ProductResponseDTO;
import cl.duoc.pedidos360.catalog.entity.Product;
import cl.duoc.pedidos360.catalog.exception.InsufficientStockException;
import cl.duoc.pedidos360.catalog.exception.ProductNotFoundException;
import cl.duoc.pedidos360.catalog.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productRepository);
    }

    private Product activeProduct(Long id, int stock) {
        return Product.builder()
                .id(id)
                .sku("SKU-" + id)
                .name("Producto " + id)
                .price(BigDecimal.TEN)
                .stock(stock)
                .active(true)
                .build();
    }

    @Test
    void creaUnProductoNuevo() {
        ProductRequestDTO request = new ProductRequestDTO("SKU-1", "Teclado", "Teclado mecanico",
                BigDecimal.valueOf(50), 20, "Perifericos");
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(1L);
            return product;
        });

        ProductResponseDTO response = productService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.sku()).isEqualTo("SKU-1");
        assertThat(response.stock()).isEqualTo(20);
    }

    @Test
    void lanzaExcepcionSiElProductoNoExiste() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(99L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void descuentaStockCuandoHayDisponible() {
        Product product = activeProduct(1L, 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponseDTO response = productService.decreaseStock(1L, 4);

        assertThat(response.stock()).isEqualTo(6);
        verify(productRepository).save(product);
    }

    @Test
    void rechazaDescuentoConStockInsuficiente() {
        Product product = activeProduct(1L, 2);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.decreaseStock(1L, 5))
                .isInstanceOf(InsufficientStockException.class);
    }
}
