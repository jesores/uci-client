package io.github.jesores.uci.transport;

/**
 * Receptor interno de eventos de línea y cierre producidos por un {@link EngineTransport}.
 */
public interface TransportListener {

    /**
     * Invocado por el hilo lector cuando el motor emite una línea completa en {@code stdout}.
     *
     * @param line línea recibida sin terminadores de línea
     */
    void onLine(String line);

    /**
     * Invocado una única vez cuando el flujo de salida del motor alcanza {@code EOF} o falla.
     *
     * @param cause excepción de E/S causante del cierre, o {@code null} si terminó por {@code EOF} normal
     */
    void onClosed(Throwable cause);
}
