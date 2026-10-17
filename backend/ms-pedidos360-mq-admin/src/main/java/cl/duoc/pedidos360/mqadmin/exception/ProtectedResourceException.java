package cl.duoc.pedidos360.mqadmin.exception;

/**
 * La operacion romperia la topologia de la que dependen orders y catalog.
 */
public class ProtectedResourceException extends RuntimeException {

    public ProtectedResourceException(String message) {
        super(message);
    }
}
