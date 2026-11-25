package io.github.jesores.uci.protocol;

import io.github.jesores.uci.api.UciEngineTerminatedException;
import io.github.jesores.uci.api.UciException;
import io.github.jesores.uci.api.UciProtocolException;
import io.github.jesores.uci.model.EngineId;
import io.github.jesores.uci.model.EngineOption;
import io.github.jesores.uci.model.Info;
import io.github.jesores.uci.model.Limit;
import io.github.jesores.uci.model.PlayResult;
import io.github.jesores.uci.model.Position;
import io.github.jesores.uci.transport.EngineTransport;
import io.github.jesores.uci.transport.TransportListener;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Coordinador interno de protocolo y máquina de estados UCI.
 *
 * <p>Nunca retiene bloqueos durante operaciones de E/S ni ejecuta callbacks del usuario
 * en el hilo lector ({@code uci-reader}).
 */
public final class UciSession implements TransportListener {

    private static final Logger LOGGER = System.getLogger(UciSession.class.getName());

    private final EngineTransport transport;
    private final Duration closeTimeout;
    private final ReentrantLock stateLock = new ReentrantLock();
    private final ReentrantLock flushLock = new ReentrantLock();
    private final Deque<String> outboundQueue = new ArrayDeque<>();
    private final Deque<CompletableFuture<Void>> readyQueue = new ArrayDeque<>();
    private final CallbackDispatcher callbacks = new CallbackDispatcher();

    private SessionState state = SessionState.UNSTARTED;
    private String discoveredName;
    private String discoveredAuthor;
    private final Map<String, EngineOption> discoveredOptions = new LinkedHashMap<>();
    private volatile EngineId engineId = EngineId.EMPTY;
    private volatile Map<String, EngineOption> options = Map.of();

    private final CompletableFuture<Void> handshakeFuture = new CompletableFuture<>();
    private ActiveSearch activeSearch;
    private CompletableFuture<Void> closeFuture;

    /**
     * Crea una sesión de protocolo asociada al transporte indicado.
     *
     * @param transport    transporte subyacente hacia el motor
     * @param closeTimeout tiempo de gracia al cerrar el motor
     */
    public UciSession(EngineTransport transport, Duration closeTimeout) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.closeTimeout = Objects.requireNonNull(closeTimeout, "closeTimeout must not be null");
    }

    /**
     * Inicia el transporte y arranca el handshake UCI ({@code uci} -> {@code uciok} -> {@code isready} -> {@code readyok}).
     *
     * @return futuro completado cuando el motor está listo en estado {@code IDLE}
     */
    public CompletableFuture<Void> startHandshake() {
        stateLock.lock();
        try {
            if (state != SessionState.UNSTARTED) {
                return CompletableFuture.failedFuture(
                        new UciProtocolException("Session handshake already started"));
            }
            state = SessionState.HANDSHAKING_UCI;
            outboundQueue.addLast(UciFormatter.uci());
        } finally {
            stateLock.unlock();
        }

        try {
            transport.start(this);
            flushOutbound();
        } catch (RuntimeException ex) {
            failSession(ex instanceof UciException uciEx
                    ? uciEx
                    : new UciException("Failed to start UCI session", ex));
        }
        return handshakeFuture;
    }

    /**
     * Devuelve los metadatos de identificación del motor capturados en el handshake.
     *
     * @return identificación inmutable
     */
    public EngineId engineId() {
        return engineId;
    }

    /**
     * Devuelve el mapa inmutable de opciones anunciadas por el motor.
     *
     * @return mapa inmutable indexado por nombre de opción
     */
    public Map<String, EngineOption> options() {
        return options;
    }

    /**
     * Indica si la sesión continúa abierta y operativa.
     *
     * @return {@code true} si no está cerrándose ni cerrada
     */
    public boolean isOpen() {
        stateLock.lock();
        try {
            return state == SessionState.IDLE
                    || state == SessionState.SEARCHING
                    || state == SessionState.STOPPING;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Envía {@code isready} y espera el correspondiente {@code readyok}.
     *
     * @return futuro completado al recibir {@code readyok}
     */
    public CompletableFuture<Void> ping() {
        CompletableFuture<Void> readyFuture = new CompletableFuture<>();
        stateLock.lock();
        try {
            ensureOperationalLocked();
            readyQueue.addLast(readyFuture);
            outboundQueue.addLast(UciFormatter.isReady());
        } catch (UciException ex) {
            return CompletableFuture.failedFuture(ex);
        } finally {
            stateLock.unlock();
        }

        flushOutbound();
        return readyFuture;
    }

    /**
     * Envía {@code ucinewgame} seguido de sincronización {@code isready}/{@code readyok}.
     *
     * @return futuro completado cuando el motor confirma disponibilidad
     */
    public CompletableFuture<Void> newGame() {
        return enqueueSynchronizedCommand("ucinewgame", UciFormatter.uciNewGame());
    }

    /**
     * Envía {@code setoption} (con o sin valor) seguido de sincronización {@code isready}/{@code readyok}.
     *
     * @param name  nombre de la opción
     * @param value valor opcional ({@link Optional#empty()} para opciones de tipo {@code button})
     * @return futuro completado cuando el motor confirma {@code readyok}
     */
    public CompletableFuture<Void> setOption(String name, Optional<String> value) {
        return enqueueSynchronizedCommand("setoption", UciFormatter.setOption(name, value));
    }

    /**
     * Inicia una búsqueda enviando {@code position} y {@code go}.
     *
     * @param position     posición a analizar
     * @param limit        límites de búsqueda
     * @param infoListener consumidor opcional para recibir líneas {@link Info} en el hilo {@code uci-callback}
     * @return futuro con el {@link PlayResult} resultante al recibir {@code bestmove}
     */
    public CompletableFuture<PlayResult> play(
            Position position,
            Limit limit,
            Consumer<Info> infoListener) {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(limit, "limit must not be null");

        String posCommand = UciFormatter.position(position);
        String goCommand = UciFormatter.go(limit);
        ActiveSearch search = new ActiveSearch(infoListener);

        stateLock.lock();
        try {
            ensureIdleLocked("go");
            state = SessionState.SEARCHING;
            activeSearch = search;
            outboundQueue.addLast(posCommand);
            outboundQueue.addLast(goCommand);
        } catch (UciException ex) {
            return CompletableFuture.failedFuture(ex);
        } finally {
            stateLock.unlock();
        }

        search.userFuture().whenComplete((res, ex) -> {
            if (ex instanceof CancellationException) {
                abandonSearch(search);
            }
        });

        flushOutbound();
        return search.userFuture();
    }

    /**
     * Solicita detener la búsqueda activa enviando {@code stop} (idempotente ante llamadas concurrentes).
     *
     * @return futuro completado cuando el motor drena {@code bestmove} y vuelve a {@code IDLE}
     */
    public CompletableFuture<Void> stop() {
        CompletableFuture<Void> termination;
        boolean shouldFlush = false;

        stateLock.lock();
        try {
            ensureOperationalLocked();
            if (state == SessionState.IDLE || activeSearch == null) {
                return CompletableFuture.completedFuture(null);
            }
            termination = activeSearch.terminationFuture();
            if (state == SessionState.SEARCHING) {
                state = SessionState.STOPPING;
                outboundQueue.addLast(UciFormatter.stop());
                shouldFlush = true;
            }
        } catch (UciException ex) {
            return CompletableFuture.failedFuture(ex);
        } finally {
            stateLock.unlock();
        }

        if (shouldFlush) {
            flushOutbound();
        }
        return termination;
    }

    /**
     * Marca la búsqueda actual como abandonada tras un timeout y envía {@code stop} si continúa activa.
     */
    public void cancelActiveSearchOnTimeout() {
        abandonSearch(null);
    }

    /**
     * Cierra la sesión enviando {@code quit}, abortando operaciones pendientes y liberando el transporte.
     *
     * @return futuro completado cuando todos los recursos han finalizado
     */
    public CompletableFuture<Void> closeAsync() {
        List<CompletableFuture<?>> pendingToFail = new ArrayList<>();
        CompletableFuture<Void> closing;
        boolean initiateClose = false;

        stateLock.lock();
        try {
            if (closeFuture != null) {
                return closeFuture;
            }
            closeFuture = new CompletableFuture<>();
            closing = closeFuture;
            if (state == SessionState.CLOSED) {
                closing.complete(null);
                return closing;
            }
            initiateClose = state != SessionState.UNSTARTED;
            state = SessionState.CLOSING;
            collectPendingLocked(pendingToFail);
            outboundQueue.clear();
        } finally {
            stateLock.unlock();
        }

        callbacks.failAll(pendingToFail, new UciEngineTerminatedException("UCI client closed"));

        if (initiateClose) {
            try {
                transport.sendLine(UciFormatter.quit());
            } catch (RuntimeException ex) {
                LOGGER.log(Level.TRACE, () -> "Ignored error sending quit during close: " + ex.getMessage());
            }
        }

        transport.closeAsync(closeTimeout).whenComplete((ignored, err) -> {
            stateLock.lock();
            try {
                state = SessionState.CLOSED;
            } finally {
                stateLock.unlock();
            }
            callbacks.shutdown();
            closing.complete(null);
        });

        return closing;
    }

    @Override
    public void onLine(String line) {
        UciMessage message = UciParser.parse(line);
        switch (message) {
            case UciMessage.IdName(var name) -> handleHandshakeMetadata(name, null, null);
            case UciMessage.IdAuthor(var author) -> handleHandshakeMetadata(null, author, null);
            case UciMessage.OptionParsed(var option) -> handleHandshakeMetadata(null, null, option);
            case UciMessage.UciOk() -> handleUciOk();
            case UciMessage.ReadyOk() -> handleReadyOk();
            case UciMessage.InfoParsed(var info) -> handleInfo(info);
            case UciMessage.BestMoveParsed(var bestMove, var ponderMove) -> handleBestMove(bestMove, ponderMove);
            case UciMessage.Unknown(var raw) ->
                LOGGER.log(Level.TRACE, () -> "Ignored unrecognized engine output: " + raw);
        }
    }

    @Override
    public void onClosed(Throwable cause) {
        boolean unexpectedClosure;
        stateLock.lock();
        try {
            unexpectedClosure = state != SessionState.CLOSING && state != SessionState.CLOSED;
        } finally {
            stateLock.unlock();
        }

        if (unexpectedClosure) {
            UciEngineTerminatedException error = cause == null
                    ? new UciEngineTerminatedException("UCI engine terminated unexpectedly (EOF)")
                    : new UciEngineTerminatedException("UCI engine connection failed", cause);
            failSession(error);
        }
    }

    private CompletableFuture<Void> enqueueSynchronizedCommand(String opName, String command) {
        CompletableFuture<Void> readyFuture = new CompletableFuture<>();
        stateLock.lock();
        try {
            ensureIdleLocked(opName);
            readyQueue.addLast(readyFuture);
            outboundQueue.addLast(command);
            outboundQueue.addLast(UciFormatter.isReady());
        } catch (UciException ex) {
            return CompletableFuture.failedFuture(ex);
        } finally {
            stateLock.unlock();
        }

        flushOutbound();
        return readyFuture;
    }

    private void handleHandshakeMetadata(String name, String author, EngineOption option) {
        stateLock.lock();
        try {
            if (state == SessionState.HANDSHAKING_UCI) {
                if (name != null) {
                    discoveredName = name;
                }
                if (author != null) {
                    discoveredAuthor = author;
                }
                if (option != null) {
                    discoveredOptions.put(option.name(), option);
                }
            }
        } finally {
            stateLock.unlock();
        }
    }

    private void handleUciOk() {
        boolean shouldFlush = false;
        stateLock.lock();
        try {
            if (state == SessionState.HANDSHAKING_UCI) {
                engineId = new EngineId(
                        Optional.ofNullable(discoveredName),
                        Optional.ofNullable(discoveredAuthor));
                options = Map.copyOf(discoveredOptions);
                state = SessionState.HANDSHAKING_READY;
                outboundQueue.addLast(UciFormatter.isReady());
                shouldFlush = true;
            }
        } finally {
            stateLock.unlock();
        }

        if (shouldFlush) {
            flushOutbound();
        }
    }

    private void handleReadyOk() {
        CompletableFuture<Void> target = null;
        stateLock.lock();
        try {
            if (state == SessionState.HANDSHAKING_READY) {
                state = SessionState.IDLE;
                target = handshakeFuture;
            } else if (!readyQueue.isEmpty()) {
                target = readyQueue.pollFirst();
            }
        } finally {
            stateLock.unlock();
        }

        if (target != null) {
            callbacks.completeVoid(target);
        }
    }

    private void handleInfo(Info info) {
        Consumer<Info> listener = null;
        stateLock.lock();
        try {
            if (activeSearch != null && (state == SessionState.SEARCHING || state == SessionState.STOPPING)) {
                listener = activeSearch.recordInfoAndGetListener(info);
            }
        } finally {
            stateLock.unlock();
        }

        if (listener != null) {
            callbacks.dispatchInfo(listener, info);
        }
    }

    private void handleBestMove(Optional<String> bestMove, Optional<String> ponderMove) {
        Runnable completionTask = null;
        stateLock.lock();
        try {
            if (activeSearch != null && (state == SessionState.SEARCHING || state == SessionState.STOPPING)) {
                ActiveSearch completed = activeSearch;
                activeSearch = null;
                state = SessionState.IDLE;
                completionTask = completed.completionTask(completed.buildResult(bestMove, ponderMove));
            } else {
                LOGGER.log(Level.DEBUG, () -> "Ignoring unexpected bestmove in state " + state);
            }
        } finally {
            stateLock.unlock();
        }

        if (completionTask != null) {
            callbacks.dispatch(completionTask, completionTask);
        }
    }

    private void abandonSearch(ActiveSearch expected) {
        boolean shouldFlush = false;
        stateLock.lock();
        try {
            if (activeSearch != null && (expected == null || activeSearch == expected)) {
                activeSearch.markAbandoned();
                if (state == SessionState.SEARCHING) {
                    state = SessionState.STOPPING;
                    outboundQueue.addLast(UciFormatter.stop());
                    shouldFlush = true;
                }
            }
        } finally {
            stateLock.unlock();
        }

        if (shouldFlush) {
            flushOutbound();
        }
    }

    private void flushOutbound() {
        flushLock.lock();
        try {
            while (true) {
                String nextLine;
                stateLock.lock();
                try {
                    if (state == SessionState.CLOSING || state == SessionState.CLOSED) {
                        outboundQueue.clear();
                        return;
                    }
                    nextLine = outboundQueue.pollFirst();
                } finally {
                    stateLock.unlock();
                }

                if (nextLine == null) {
                    return;
                }
                transport.sendLine(nextLine);
            }
        } catch (RuntimeException ex) {
            UciException failure = ex instanceof UciException uciEx
                    ? uciEx
                    : new UciEngineTerminatedException("Failed writing command to UCI engine", ex);
            failSession(failure);
        } finally {
            flushLock.unlock();
        }
    }

    private void failSession(UciException error) {
        List<CompletableFuture<?>> pendingToFail = new ArrayList<>();
        stateLock.lock();
        try {
            if (state == SessionState.CLOSED) {
                return;
            }
            state = SessionState.CLOSED;
            outboundQueue.clear();
            collectPendingLocked(pendingToFail);
        } finally {
            stateLock.unlock();
        }

        LOGGER.log(Level.DEBUG, () -> "Session failed: " + error.getMessage());
        callbacks.failAll(pendingToFail, error);
        transport.closeAsync(closeTimeout).whenComplete((ignored, err) -> callbacks.shutdown());
    }

    private void collectPendingLocked(List<CompletableFuture<?>> out) {
        if (!handshakeFuture.isDone()) {
            out.add(handshakeFuture);
        }
        while (!readyQueue.isEmpty()) {
            out.add(readyQueue.pollFirst());
        }
        if (activeSearch != null) {
            activeSearch.collectPendingFutures(out);
            activeSearch = null;
        }
    }

    private void ensureOperationalLocked() {
        if (state == SessionState.CLOSING || state == SessionState.CLOSED) {
            throw new UciEngineTerminatedException("UCI session is closed");
        }
        if (state == SessionState.UNSTARTED
                || state == SessionState.HANDSHAKING_UCI
                || state == SessionState.HANDSHAKING_READY) {
            throw new UciProtocolException("UCI handshake has not completed yet");
        }
    }

    private void ensureIdleLocked(String operation) {
        ensureOperationalLocked();
        if (state != SessionState.IDLE) {
            throw new UciProtocolException("Cannot execute '" + operation + "' while engine is in state " + state);
        }
    }
}
