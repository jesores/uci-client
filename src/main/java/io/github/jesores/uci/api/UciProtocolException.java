package io.github.jesores.uci.api;

/**
 * Excepción lanzada ante violaciones de estado o uso inválido de la sesión del protocolo UCI
 * (por ejemplo, iniciar dos búsquedas concurrentes o configurar opciones durante una búsqueda activa).
 */
public final class UciProtocolException extends UciException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea una excepción de protocolo con el mensaje indicado.
     *
     * @param message descripción de la violación de protocolo o estado
     */
    public UciProtocolException(String message) {
        super(message);
    }

    /**
     * Crea una excepción de protocolo con mensaje y causa subyacente.
     *
     * @param message descripción del error
     * @param cause   causa original
     */
    public UciProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
