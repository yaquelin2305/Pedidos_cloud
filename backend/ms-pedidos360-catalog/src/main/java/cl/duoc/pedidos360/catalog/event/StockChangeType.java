package cl.duoc.pedidos360.catalog.event;

/**
 * Tipo de cambio que origino un {@link StockChangedEvent}.
 */
public enum StockChangeType {
    PRODUCT_CREATED,
    PRODUCT_UPDATED,
    STOCK_ADJUSTED,
    STOCK_DECREASED,
    PRODUCT_DEACTIVATED
}
