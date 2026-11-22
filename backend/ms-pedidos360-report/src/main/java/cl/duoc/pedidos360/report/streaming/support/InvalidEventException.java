package cl.duoc.pedidos360.report.streaming.support;

/**
 * El payload de un evento no cumple el contrato. No se reintenta: va directo a la DLT.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
