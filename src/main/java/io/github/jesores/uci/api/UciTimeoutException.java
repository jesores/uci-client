package io.github.jesores.uci.api;

/**
 * Excepción lanzada cuando una operación con el motor UCI excede el tiempo máximo de espera configurado.
 */
public final class UciTimeoutException extends UciException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea una excepción de timeout con el mensaje indicado.
     *
     * @param message descripción del timeout
     */
    public UciTimeoutException(String message) {
        super(message);
    }

    /**
     * Crea una excepción de timeout con mensaje y causa subyacente.
     *
     * @param message descripción del timeout
     * @param cause   causa original
     */
    public UciTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
