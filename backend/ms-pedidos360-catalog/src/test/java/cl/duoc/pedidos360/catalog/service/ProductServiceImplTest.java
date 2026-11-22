package cl.duoc.pedidos360.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
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
import cl.duoc.pedidos360.catalog.event.StockChangeType;
import cl.duoc.pedidos360.catalog.event.StockChangedEvent;
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

        List<StockLowEvent> alerts = publishedEvents(StockLowEvent.class);
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).productId()).isEqualTo(1L);
        assertThat(alerts.get(0).stock()).isEqualTo(4);
        assertThat(alerts.get(0).threshold()).isEqualTo(LOW_STOCK_THRESHOLD);
        assertThat(alerts.get(0).eventId()).isNotNull();
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

        verify(eventPublisher, never()).publishEvent(any(StockLowEvent.class));
    }

    @Test
    void noRepiteLaAlertaSiElStockYaEstabaBajo() {
        Product product = activeProduct(1L, 4);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.decreaseStock(1L, 2);

        verify(eventPublisher, never()).publishEvent(any(StockLowEvent.class));
    }

    @Test
    void noPublicaAlertaSiElDescuentoFallaPorStockInsuficiente() {
        Product product = activeProduct(1L, 8);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.decreaseStock(1L, 9))
                .isInstanceOf(InsufficientStockException.class);
        verify(eventPublisher, never()).publishEvent(any(StockLowEvent.class));
    }

    @Test
    void publicaCambioDeStockAlCrearSinStockAnterior() {
        ProductRequestDTO request = new ProductRequestDTO("SKU-1", "Teclado", "Teclado mecanico",
                BigDecimal.valueOf(50), 3, "Perifericos");
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(1L);
            return product;
        });

        productService.create(request);

        StockChangedEvent event = singleStockChangedEvent();
        assertThat(event.changeType()).isEqualTo(StockChangeType.PRODUCT_CREATED);
        assertThat(event.productId()).isEqualTo(1L);
        assertThat(event.sku()).isEqualTo("SKU-1");
        assertThat(event.previousStock()).isNull();
        assertThat(event.stock()).isEqualTo(3);
        assertThat(event.lowStockThreshold()).isEqualTo(LOW_STOCK_THRESHOLD);
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void publicaCambioDeStockAlEditar() {
        Product product = activeProduct(1L, 20);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductRequestDTO request = new ProductRequestDTO("SKU-1", "Teclado", "Teclado mecanico",
                BigDecimal.valueOf(50), 15, "Perifericos");

        productService.update(1L, request);

        StockChangedEvent event = singleStockChangedEvent();
        assertThat(event.changeType()).isEqualTo(StockChangeType.PRODUCT_UPDATED);
        assertThat(event.previousStock()).isEqualTo(20);
        assertThat(event.stock()).isEqualTo(15);
    }

    @Test
    void publicaCambioDeStockAlAjustarElStock() {
        Product product = activeProduct(1L, 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateStock(1L, 30);

        StockChangedEvent event = singleStockChangedEvent();
        assertThat(event.changeType()).isEqualTo(StockChangeType.STOCK_ADJUSTED);
        assertThat(event.previousStock()).isEqualTo(10);
        assertThat(event.stock()).isEqualTo(30);
        assertThat(event.active()).isTrue();
    }

    @Test
    void publicaCambioDeStockAlDescontar() {
        Product product = activeProduct(1L, 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.decreaseStock(1L, 4);

        StockChangedEvent event = singleStockChangedEvent();
        assertThat(event.changeType()).isEqualTo(StockChangeType.STOCK_DECREASED);
        assertThat(event.previousStock()).isEqualTo(10);
        assertThat(event.stock()).isEqualTo(6);
    }

    @Test
    void publicaCambioDeStockAlDesactivar() {
        Product product = activeProduct(1L, 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.deactivate(1L);

        StockChangedEvent event = singleStockChangedEvent();
        assertThat(event.changeType()).isEqualTo(StockChangeType.PRODUCT_DEACTIVATED);
        assertThat(event.active()).isFalse();
        assertThat(event.stock()).isEqualTo(10);
    }

    @Test
    void noPublicaCambioDeStockSiElDescuentoFalla() {
        Product product = activeProduct(1L, 2);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.decreaseStock(1L, 5))
                .isInstanceOf(InsufficientStockException.class);
        verify(eventPublisher, never()).publishEvent(any(StockChangedEvent.class));
    }

    @Test
    void rechazaDescuentoConStockInsuficiente() {
        Product product = activeProduct(1L, 2);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.decreaseStock(1L, 5))
                .isInstanceOf(InsufficientStockException.class);
    }

    private StockChangedEvent singleStockChangedEvent() {
        List<StockChangedEvent> events = publishedEvents(StockChangedEvent.class);
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    private <T> List<T> publishedEvents(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(type::isInstance).map(type::cast).toList();
    }
}
