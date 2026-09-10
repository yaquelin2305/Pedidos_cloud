package cl.duoc.pedidos360.orders.entity;

/**
 * Estados del pedido. CANCELADO solo es alcanzable desde CREADO o ACEPTADO: una vez que el
 * pedido entra en preparacion, ya no se puede abortar por esta via (regla de negocio del caso).
 * La tabla de transiciones permitidas vive en OrderStatusValidator, no aqui, para mantener el
 * enum como un simple conjunto de valores.
 */
public enum OrderStatus {
    CREADO,
    ACEPTADO,
    EN_PREPARACION,
    DESPACHADO,
    ENTREGADO,
    CANCELADO
}
