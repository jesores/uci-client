package io.github.jesores.uci.api;

/**
 * Excepción lanzada cuando el proceso del motor UCI termina inesperadamente ({@code EOF}),
 * falla la canalización de E/S o se invoca una operación sobre un cliente ya cerrado.
 */
public final class UciEngineTerminatedException extends UciException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea una excepción indicando terminación del motor o cliente cerrado.
     *
     * @param message descripción del cierre o fallo
     */
    public UciEngineTerminatedException(String message) {
        super(message);
    }

    /**
     * Crea una excepción indicando terminación del motor con causa subyacente.
     *
     * @param message descripción del cierre o fallo
     * @param cause   causa de E/S o proceso
     */
    public UciEngineTerminatedException(String message, Throwable cause) {
        super(message, cause);
    }
}
