package io.github.jesores.uci.api;

import io.github.jesores.uci.model.EngineId;
import io.github.jesores.uci.model.EngineOption;
import io.github.jesores.uci.model.Info;
import io.github.jesores.uci.model.Limit;
import io.github.jesores.uci.model.PlayResult;
import io.github.jesores.uci.model.Position;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Cliente síncrono bloqueante para comunicarse con un motor de ajedrez UCI (inspirado en {@code chess.engine.SimpleEngine}).
 *
 * <p><b>Garantías de concurrencia (<i>thread-safety</i>):</b> Todas las implementaciones son seguras para
 * invocación concurrente. Mientras un hilo bloquea en {@link #play(Position, Limit)}, otro hilo puede invocar
 * de forma segura {@link #stop()}, {@link #ping()} o {@link #close()}. Iniciar dos búsquedas simultáneas
 * lanzará {@link UciProtocolException}. Los callbacks {@code Consumer<Info>} se ejecutan en el hilo dedicado
 * {@code uci-callback}, nunca en el hilo lector de {@code stdout}.
 */
public interface UciClient extends AutoCloseable {

    /**
     * Lanza el ejecutable del motor UCI con configuración predeterminada y bloquea hasta completar el handshake.
     *
     * @param executable ruta al binario del motor UCI
     * @return cliente síncrono inicializado
     * @throws NullPointerException         si {@code executable} es {@code null}
     * @throws UciTimeoutException          si el handshake excede el tiempo configurado
     * @throws UciEngineTerminatedException si el proceso termina inesperadamente durante el arranque
     * @throws UciException                 si ocurre un error al iniciar el proceso
     */
    static UciClient open(Path executable) {
        Objects.requireNonNull(executable, "executable must not be null");
        return open(UciConfig.of(executable));
    }

    /**
     * Lanza el motor UCI según la configuración suministrada y bloquea hasta completar el handshake.
     *
     * <p>Si ocurre un timeout o fallo durante el arranque, todos los recursos asociados al proceso se liberan
     * antes de propagar la excepción.
     *
     * @param config configuración del cliente UCI
     * @return cliente síncrono inicializado
     * @throws NullPointerException         si {@code config} es {@code null}
     * @throws UciTimeoutException          si el handshake excede {@link UciConfig#handshakeTimeout()}
     * @throws UciEngineTerminatedException si el proceso termina prematuramente
     * @throws UciException                 si ocurre un error al iniciar el proceso
     */
    static UciClient open(UciConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        return DefaultUciClient.open(config);
    }

    /**
     * Devuelve la identificación ({@code name} y {@code author}) anunciada por el motor.
     *
     * @return metadatos inmutables de identificación
     */
    EngineId engineId();

    /**
     * Devuelve las opciones anunciadas por el motor durante el handshake.
     *
     * @return mapa inmutable indexado por nombre de opción
     */
    Map<String, EngineOption> options();

    /**
     * Indica si el cliente continúa abierto y operativo.
     *
     * @return {@code true} si el cliente sigue abierto
     */
    boolean isOpen();

    /**
     * Verifica disponibilidad del motor ({@code isready}/{@code readyok}) usando {@link UciConfig#defaultTimeout()}.
     *
     * @throws UciTimeoutException          si excede el timeout por defecto
     * @throws UciEngineTerminatedException si el motor termina o el cliente está cerrado
     */
    void ping();

    /**
     * Verifica disponibilidad del motor ({@code isready}/{@code readyok}) con un timeout explícito.
     *
     * @param timeout tiempo máximo de espera estrictamente positivo
     * @throws NullPointerException         si {@code timeout} es {@code null}
     * @throws IllegalArgumentException     si {@code timeout} es cero o negativo
     * @throws UciTimeoutException          si excede {@code timeout}
     * @throws UciEngineTerminatedException si el motor termina o el cliente está cerrado
     */
    void ping(Duration timeout);

    /**
     * Notifica una nueva partida ({@code ucinewgame}) y espera confirmación {@code readyok}.
     *
     * @throws UciProtocolException         si hay una búsqueda activa
     * @throws UciTimeoutException          si excede {@link UciConfig#defaultTimeout()}
     * @throws UciEngineTerminatedException si el motor termina o el cliente está cerrado
     */
    void newGame();

    /**
     * Notifica una nueva partida ({@code ucinewgame}) con un timeout explícito.
     *
     * @param timeout tiempo máximo de espera estrictamente positivo
     * @throws NullPointerException         si {@code timeout} es {@code null}
     * @throws IllegalArgumentException     si {@code timeout} es cero o negativo
     * @throws UciProtocolException         si hay una búsqueda activa
     * @throws UciTimeoutException          si excede {@code timeout}
     * @throws UciEngineTerminatedException si el motor termina o el cliente está cerrado
     */
    void newGame(Duration timeout);

    /**
     * Configura una opción UCI con valor ({@code setoption name <name> value <value>}) y sincroniza con {@code readyok}.
     *
     * @param name  nombre no vacío de la opción
     * @param value valor a establecer
     * @throws NullPointerException         si {@code name} o {@code value} son {@code null}
     * @throws IllegalArgumentException     si {@code name} está en blanco o contiene saltos de línea
     * @throws UciProtocolException         si hay una búsqueda activa
     * @throws UciTimeoutException          si excede {@link UciConfig#defaultTimeout()}
     * @throws UciEngineTerminatedException si el motor termina o el cliente está cerrado
     */
    void setOption(String name, String value);

    /**
     * Acciona una opción UCI de tipo {@code button} ({@code setoption name <name>}) y sincroniza con {@code readyok}.
     *
     * @param name nombre no vacío del botón
     * @throws NullPointerException         si {@code name} es {@code null}
     * @throws IllegalArgumentException     si {@code name} está en blanco o contiene saltos de línea
     * @throws UciProtocolException         si hay una búsqueda activa
     * @throws UciTimeoutException          si excede {@link UciConfig#defaultTimeout()}
     * @throws UciEngineTerminatedException si el motor termina o el cliente está cerrado
     */
    void triggerButton(String name);

    /**
     * Ejecuta una búsqueda síncrona usando {@link UciConfig#defaultTimeout()}.
     *
     * @param position posición a analizar
     * @param limit    límites de búsqueda
     * @return {@link PlayResult} recibido con {@code bestmove}
     * @throws NullPointerException         si {@code position} o {@code limit} son {@code null}
     * @throws UciProtocolException         si ya existe otra búsqueda en curso
     * @throws UciTimeoutException          si la búsqueda supera el timeout (se envía {@code stop} automáticamente)
     * @throws UciEngineTerminatedException si el motor termina inesperadamente
     */
    PlayResult play(Position position, Limit limit);

    /**
     * Ejecuta una búsqueda síncrona notificando líneas {@link Info} parciales y usando {@link UciConfig#defaultTimeout()}.
     *
     * @param position     posición a analizar
     * @param limit        límites de búsqueda
     * @param infoListener consumidor no nulo ejecutado en el hilo {@code uci-callback}
     * @return {@link PlayResult} recibido con {@code bestmove}
     * @throws NullPointerException         si {@code position}, {@code limit} o {@code infoListener} son {@code null}
     * @throws UciProtocolException         si ya existe otra búsqueda en curso
     * @throws UciTimeoutException          si la búsqueda supera el timeout (se envía {@code stop} automáticamente)
     * @throws UciEngineTerminatedException si el motor termina inesperadamente
     */
    PlayResult play(Position position, Limit limit, Consumer<Info> infoListener);

    /**
     * Ejecuta una búsqueda síncrona con consumidor opcional de {@link Info} y un timeout explícito.
     *
     * <p>Si se alcanza {@code timeout}, el cliente envía {@code stop} automáticamente al motor antes de lanzar
     * {@link UciTimeoutException}.
     *
     * @param position     posición a analizar
     * @param limit        límites de búsqueda
     * @param infoListener consumidor no nulo de eventos {@link Info}
     * @param timeout      tiempo máximo de espera estrictamente positivo
     * @return {@link PlayResult} recibido con {@code bestmove}
     * @throws NullPointerException         si algún argumento es {@code null}
     * @throws IllegalArgumentException     si {@code timeout} es cero o negativo
     * @throws UciProtocolException         si ya existe otra búsqueda en curso
     * @throws UciTimeoutException          si la búsqueda supera {@code timeout}
     * @throws UciEngineTerminatedException si el motor termina inesperadamente
     */
    PlayResult play(Position position, Limit limit, Consumer<Info> infoListener, Duration timeout);

    /**
     * Detiene la búsqueda activa (si existe) y espera a que el motor drene {@code bestmove}.
     *
     * @throws UciTimeoutException          si el motor no responde {@code bestmove} dentro de {@link UciConfig#defaultTimeout()}
     * @throws UciEngineTerminatedException si el motor termina inesperadamente o el cliente está cerrado
     */
    void stop();

    /**
     * Cierra ordenadamente la sesión enviando {@code quit} y liberando todos los recursos del proceso.
     */
    @Override
    void close();
}
