package cl.duoc.pedidos360.mqadmin.dto;

/**
 * Tipo de cola que el administrador permite crear. QUORUM replica la cola en los nodos del cluster.
 */
public enum QueueType {
    CLASSIC,
    QUORUM
}
