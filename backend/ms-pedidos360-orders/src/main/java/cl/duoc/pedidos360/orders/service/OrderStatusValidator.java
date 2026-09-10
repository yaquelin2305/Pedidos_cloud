package cl.duoc.pedidos360.orders.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import cl.duoc.pedidos360.orders.entity.OrderStatus;
import cl.duoc.pedidos360.orders.exception.InvalidOrderStatusTransitionException;

import static cl.duoc.pedidos360.orders.entity.OrderStatus.ACEPTADO;
import static cl.duoc.pedidos360.orders.entity.OrderStatus.CANCELADO;
import static cl.duoc.pedidos360.orders.entity.OrderStatus.CREADO;
import static cl.duoc.pedidos360.orders.entity.OrderStatus.DESPACHADO;
import static cl.duoc.pedidos360.orders.entity.OrderStatus.EN_PREPARACION;
import static cl.duoc.pedidos360.orders.entity.OrderStatus.ENTREGADO;

/**
 * Tabla de transiciones permitidas del pedido. No es un componente Spring a proposito: no tiene
 * dependencias y as? se puede testear como una clase de utilidad aislada, sin levantar contexto.
 *
 * CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO, con CANCELADO alcanzable solo
 * desde CREADO o ACEPTADO. En particular, DESPACHADO exige haber pasado por ACEPTADO (no existe
 * un salto directo desde CREADO ni desde ningun otro estado que no sea EN_PREPARACION).
 */
public final class OrderStatusValidator {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(CREADO, EnumSet.of(ACEPTADO, CANCELADO));
        ALLOWED_TRANSITIONS.put(ACEPTADO, EnumSet.of(EN_PREPARACION, CANCELADO));
        ALLOWED_TRANSITIONS.put(EN_PREPARACION, EnumSet.of(DESPACHADO));
        ALLOWED_TRANSITIONS.put(DESPACHADO, EnumSet.of(ENTREGADO));
        ALLOWED_TRANSITIONS.put(ENTREGADO, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(CANCELADO, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStatusValidator() {
    }

    public static void validateTransition(OrderStatus from, OrderStatus to) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new InvalidOrderStatusTransitionException(from, to);
        }
    }
}
