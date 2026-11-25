package io.github.jesores.uci.api;

/**
 * Excepción base no verificada para errores de comunicación, protocolo o ciclo de vida con un motor UCI.
 *
 * <p>Segura para propagarse entre hilos en operaciones síncronas y asíncronas.
 */
public class UciException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea una excepción con el mensaje descriptivo indicado.
     *
     * @param message descripción del error
     */
    public UciException(String message) {
        super(message);
    }

    /**
     * Crea una excepción con mensaje descriptivo y causa raíz.
     *
     * @param message descripción del error
     * @param cause   causa subyacente
     */
    public UciException(String message, Throwable cause) {
        super(message, cause);
    }
}
