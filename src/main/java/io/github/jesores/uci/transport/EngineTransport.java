package io.github.jesores.uci.transport;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Abstracción interna de transporte bidireccional por líneas hacia un motor UCI.
 */
public interface EngineTransport extends AutoCloseable {

    /**
     * Inicia la lectura asíncrona de líneas notificando al {@link TransportListener} suministrado.
     *
     * @param listener receptor no nulo de eventos del transporte
     */
    void start(TransportListener listener);

    /**
     * Envía una línea de comando al motor seguida de salto de línea y vacía el búfer de salida.
     *
     * @param line comando UCI sin caracteres {@code \r} ni {@code \n}
     */
    void sendLine(String line);

    /**
     * Cierra de forma asíncrona los flujos de E/S y garantiza la finalización de los recursos subyacentes.
     *
     * @param gracePeriod tiempo de gracia antes de forzar la terminación del proceso
     * @return futuro completado cuando los recursos se han liberado
     */
    CompletableFuture<Void> closeAsync(Duration gracePeriod);

    @Override
    void close();
}
