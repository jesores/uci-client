package io.github.jesores.uci.api;

import io.github.jesores.uci.model.EngineId;
import io.github.jesores.uci.model.EngineOption;
import io.github.jesores.uci.model.Info;
import io.github.jesores.uci.model.Limit;
import io.github.jesores.uci.model.PlayResult;
import io.github.jesores.uci.model.Position;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Cliente asíncrono no bloqueante basado en {@link CompletableFuture} para comunicarse con un motor UCI.
 *
 * <p><b>Garantías de concurrencia (<i>thread-safety</i>):</b> Todas las implementaciones devueltas por
 * esta interfaz son seguras para invocación desde múltiples hilos. El protocolo UCI solo admite una búsqueda
 * activa a la vez; invocar {@link #play(Position, Limit)}, {@link #newGame()} o {@link #setOption(String, String)}
 * mientras otra búsqueda está en curso completará excepcionalmente el futuro devuelto con {@link UciProtocolException}.
 * En cambio, {@link #stop()}, {@link #ping()} y {@link #closeAsync()} pueden invocarse de forma segura mientras
 * una búsqueda está en progreso. Los consumidores {@code Consumer<Info>} se ejecutan siempre en un hilo interno
 * dedicado ({@code uci-callback}), nunca en el hilo lector de {@code stdout}.
 */
public interface AsyncUciClient extends AutoCloseable {

    /**
     * Lanza el ejecutable indicado con configuración predeterminada y completa el handshake inicial UCI.
     *
     * @param executable ruta al binario del motor UCI
     * @return futuro completado con el {@link AsyncUciClient} listo para operar, o completado excepcionalmente
     *         con {@link UciTimeoutException}, {@link UciEngineTerminatedException} o {@link UciException}
     * @throws NullPointerException si {@code executable} es {@code null}
     */
    static CompletableFuture<AsyncUciClient> open(Path executable) {
        Objects.requireNonNull(executable, "executable must not be null");
        return open(UciConfig.of(executable));
    }

    /**
     * Lanza el motor UCI según la configuración suministrada y completa el handshake ({@code uciok} + {@code readyok}).
     *
     * <p>Si el lanzamiento o el handshake fallan o exceden {@link UciConfig#handshakeTimeout()},
     * el subproceso y sus descriptores de E/S se liberan automáticamente antes de completar el futuro con error.
     *
     * @param config configuración del cliente UCI
     * @return futuro con el cliente inicializado
     * @throws NullPointerException si {@code config} es {@code null}
     */
    static CompletableFuture<AsyncUciClient> open(UciConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        return DefaultAsyncUciClient.open(config);
    }

    /**
     * Devuelve la identificación ({@code name} y {@code author}) anunciada por el motor en el handshake.
     *
     * @return metadatos inmutables de identificación
     */
    EngineId engineId();

    /**
     * Devuelve las opciones anunciadas por el motor durante el handshake inicial.
     *
     * @return mapa inmutable indexado por nombre de opción
     */
    Map<String, EngineOption> options();

    /**
     * Indica si la sesión con el motor sigue abierta y operativa.
     *
     * @return {@code true} si el cliente sigue abierto
     */
    boolean isOpen();

    /**
     * Sincroniza con el motor enviando {@code isready} y esperando {@code readyok}.
     *
     * @return futuro completado al recibir {@code readyok}
     */
    CompletableFuture<Void> ping();

    /**
     * Notifica el inicio de una nueva partida ({@code ucinewgame}) sincronizando con {@code isready}/{@code readyok}.
     *
     * @return futuro completado cuando el motor está listo para la nueva partida
     */
    CompletableFuture<Void> newGame();

    /**
     * Configura el valor de una opción del motor ({@code setoption name <name> value <value>})
     * y sincroniza mediante {@code isready}/{@code readyok}.
     *
     * @param name  nombre no vacío de la opción
     * @param value valor a asignar (cadena vacía se traduce a {@code <empty>} según la especificación UCI)
     * @return futuro completado cuando el cambio ha sido procesado
     * @throws NullPointerException     si {@code name} o {@code value} son {@code null}
     * @throws IllegalArgumentException si {@code name} está en blanco o los argumentos contienen saltos de línea
     */
    CompletableFuture<Void> setOption(String name, String value);

    /**
     * Acciona una opción de tipo {@code button} ({@code setoption name <name>})
     * y sincroniza mediante {@code isready}/{@code readyok}.
     *
     * @param name nombre no vacío de la opción tipo botón
     * @return futuro completado cuando el motor confirma disponibilidad
     * @throws NullPointerException     si {@code name} es {@code null}
     * @throws IllegalArgumentException si {@code name} está en blanco o contiene saltos de línea
     */
    CompletableFuture<Void> triggerButton(String name);

    /**
     * Ejecuta una búsqueda sobre la posición dada respetando los límites indicados.
     *
     * @param position posición a analizar
     * @param limit    límites para el comando {@code go}
     * @return futuro con el {@link PlayResult} emitido al recibir {@code bestmove}
     * @throws NullPointerException si {@code position} o {@code limit} son {@code null}
     */
    CompletableFuture<PlayResult> play(Position position, Limit limit);

    /**
     * Ejecuta una búsqueda notificando cada línea {@link Info} parcial al consumidor suministrado.
     *
     * <p>Se garantiza que todas las invocaciones a {@code infoListener} ocurren en orden en el hilo
     * {@code uci-callback} y finalizan antes de que se complete el {@link CompletableFuture} devuelto.
     * Si el futuro devuelto se cancela ({@link CompletableFuture#cancel(boolean)}), se envía automáticamente
     * {@code stop} al motor.
     *
     * @param position     posición a analizar
     * @param limit        límites para el comando {@code go}
     * @param infoListener consumidor no nulo de eventos {@link Info}
     * @return futuro con el {@link PlayResult} final
     * @throws NullPointerException si {@code position}, {@code limit} o {@code infoListener} son {@code null}
     */
    CompletableFuture<PlayResult> play(Position position, Limit limit, Consumer<Info> infoListener);

    /**
     * Detiene de forma segura e idempotente cualquier búsqueda en curso enviando {@code stop}.
     *
     * <p>Si no hay ninguna búsqueda activa, devuelve un futuro ya completado. Si hay una búsqueda activa,
     * el futuro devuelto se completa cuando el motor emite el correspondiente {@code bestmove} y la sesión
     * retorna al estado inactivo.
     *
     * @return futuro completado cuando no queda ninguna búsqueda activa
     */
    CompletableFuture<Void> stop();

    /**
     * Cierra asíncronamente la sesión enviando {@code quit} y liberando flujos y subproceso.
     *
     * <p>Operación idempotente.
     *
     * @return futuro completado cuando todos los recursos del motor han sido liberados
     */
    CompletableFuture<Void> closeAsync();

    /**
     * Cierra el cliente esperando de manera acotada la liberación de los recursos del subproceso.
     */
    @Override
    void close();
}
