package cl.duoc.pedidos360.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import cl.duoc.pedidos360.catalog.config.CatalogProperties;
import cl.duoc.pedidos360.catalog.dto.ProductRequestDTO;
import cl.duoc.pedidos360.catalog.dto.ProductResponseDTO;
import cl.duoc.pedidos360.catalog.entity.Product;
import cl.duoc.pedidos360.catalog.event.StockLowEvent;
import cl.duoc.pedidos360.catalog.exception.InsufficientStockException;
import cl.duoc.pedidos360.catalog.exception.ProductNotFoundException;
import cl.duoc.pedidos360.catalog.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    private static final int LOW_STOCK_THRESHOLD = 5;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productRepository, eventPublisher,
                new CatalogProperties(LOW_STOCK_THRESHOLD));
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
    void autogeneraElSkuCuandoVieneVacio() {
        ProductRequestDTO request = new ProductRequestDTO("", "Teclado", "Teclado mecanico",
                BigDecimal.valueOf(50), 20, "Perifericos");
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponseDTO response = productService.create(request);

        assertThat(response.sku()).isNotBlank();
    }

    @Test
    void ajustaElStockAlValorAbsolutoRecibido() {
        Product product = activeProduct(1L, 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponseDTO response = productService.updateStock(1L, 50);

        assertThat(response.stock()).isEqualTo(50);
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
    void publicaAlertaCuandoElDescuentoCruzaElUmbral() {
        Product product = activeProduct(1L, 8);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.decreaseStock(1L, 4);

        ArgumentCaptor<StockLowEvent> event = ArgumentCaptor.forClass(StockLowEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().productId()).isEqualTo(1L);
        assertThat(event.getValue().stock()).isEqualTo(4);
        assertThat(event.getValue().threshold()).isEqualTo(LOW_STOCK_THRESHOLD);
        assertThat(event.getValue().eventId()).isNotNull();
    }

    @Test
    void publicaAlertaCuandoElAjusteDejaElStockJustoEnElUmbral() {
        Product product = activeProduct(1L, 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateStock(1L, LOW_STOCK_THRESHOLD);

        verify(eventPublisher).publishEvent(any(StockLowEvent.class));
    }

    @Test
    void publicaAlertaCuandoLaEdicionBajaElStockBajoElUmbral() {
        Product product = activeProduct(1L, 20);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductRequestDTO request = new ProductRequestDTO("SKU-1", "Teclado", "Teclado mecanico",
                BigDecimal.valueOf(50), 2, "Perifericos");

        productService.update(1L, request);

        verify(eventPublisher).publishEvent(any(StockLowEvent.class));
    }

    @Test
    void noPublicaAlertaSiElStockSigueSobreElUmbral() {
        Product product = activeProduct(1L, 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.decreaseStock(1L, 4);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void noRepiteLaAlertaSiElStockYaEstabaBajo() {
        Product product = activeProduct(1L, 4);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.decreaseStock(1L, 2);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void noPublicaAlertaSiElDescuentoFallaPorStockInsuficiente() {
        Product product = activeProduct(1L, 8);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.decreaseStock(1L, 9))
                .isInstanceOf(InsufficientStockException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rechazaDescuentoConStockInsuficiente() {
        Product product = activeProduct(1L, 2);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.decreaseStock(1L, 5))
                .isInstanceOf(InsufficientStockException.class);
    }
}
