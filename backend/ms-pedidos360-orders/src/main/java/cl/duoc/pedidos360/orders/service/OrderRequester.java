package cl.duoc.pedidos360.orders.service;

/**
 * Identidad del usuario autenticado que invoca el servicio, extraida del JWT por el controller.
 * No es un DTO de la API: es el mecanismo interno con el que el Service valida ownership y
 * aplica reglas que dependen del rol (por ejemplo, quien puede cancelar desde que estado).
 */
public record OrderRequester(String customerId, boolean privileged) {
}
