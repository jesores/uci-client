package io.github.jesores.uci.api;

import io.github.jesores.uci.model.EngineId;
import io.github.jesores.uci.model.EngineOption;
import io.github.jesores.uci.model.Info;
import io.github.jesores.uci.model.Limit;
import io.github.jesores.uci.model.PlayResult;
import io.github.jesores.uci.model.Position;
import io.github.jesores.uci.protocol.UciSession;
import io.github.jesores.uci.transport.EngineTransport;
import io.github.jesores.uci.transport.ProcessTransport;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

final class DefaultAsyncUciClient implements AsyncUciClient {

    private static final Logger LOGGER = System.getLogger(DefaultAsyncUciClient.class.getName());

    private final UciSession session;
    private final UciConfig config;

    private DefaultAsyncUciClient(UciSession session, UciConfig config) {
        this.session = session;
        this.config = config;
    }

    static CompletableFuture<AsyncUciClient> open(UciConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        EngineTransport transport;
        try {
            transport = ProcessTransport.launch(
                    config.executable(),
                    config.arguments(),
                    config.workingDirectory());
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(ex);
        }
        return openWithTransport(transport, config).thenApply(c -> c);
    }

    static CompletableFuture<DefaultAsyncUciClient> openWithTransport(
            EngineTransport transport,
            UciConfig config) {
        Objects.requireNonNull(transport, "transport must not be null");
        Objects.requireNonNull(config, "config must not be null");

        UciSession session = new UciSession(transport, config.closeTimeout());
        DefaultAsyncUciClient client = new DefaultAsyncUciClient(session, config);
        CompletableFuture<DefaultAsyncUciClient> result = new CompletableFuture<>();

        long timeoutMillis = Math.max(1L, config.handshakeTimeout().toMillis());
        session.startHandshake()
                .copy()
                .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .whenComplete((ignored, error) -> {
                    if (error == null) {
                        result.complete(client);
                    } else {
                        Throwable cause = unwrap(error);
                        Throwable mapped = cause instanceof TimeoutException
                                ? new UciTimeoutException(
                                        "Timed out waiting for UCI handshake (" + config.handshakeTimeout() + ")",
                                        cause)
                                : cause;
                        session.closeAsync().whenComplete((v, closeErr) -> {
                            if (closeErr != null) {
                                LOGGER.log(Level.DEBUG, () -> "Cleanup error after failed handshake: " + closeErr.getMessage());
                            }
                            result.completeExceptionally(mapped);
                        });
                    }
                });

        return result;
    }

    UciConfig config() {
        return config;
    }

    void onPlayTimeout() {
        session.cancelActiveSearchOnTimeout();
    }

    @Override
    public EngineId engineId() {
        return session.engineId();
    }

    @Override
    public Map<String, EngineOption> options() {
        return session.options();
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public CompletableFuture<Void> ping() {
        return session.ping();
    }

    @Override
    public CompletableFuture<Void> newGame() {
        return session.newGame();
    }

    @Override
    public CompletableFuture<Void> setOption(String name, String value) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return session.setOption(name, Optional.of(value));
    }

    @Override
    public CompletableFuture<Void> triggerButton(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return session.setOption(name, Optional.empty());
    }

    @Override
    public CompletableFuture<PlayResult> play(Position position, Limit limit) {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(limit, "limit must not be null");
        return session.play(position, limit, null);
    }

    @Override
    public CompletableFuture<PlayResult> play(
            Position position,
            Limit limit,
            Consumer<Info> infoListener) {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(limit, "limit must not be null");
        Objects.requireNonNull(infoListener, "infoListener must not be null");
        return session.play(position, limit, infoListener);
    }

    @Override
    public CompletableFuture<Void> stop() {
        return session.stop();
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        return session.closeAsync();
    }

    @Override
    public void close() {
        long waitMillis = Math.max(500L, config.closeTimeout().toMillis() + 1000L);
        try {
            closeAsync().get(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ex) {
            LOGGER.log(Level.WARNING, () -> "Timed out or failed awaiting close(): " + ex.getMessage());
        }
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException || error instanceof ExecutionException) {
            return error.getCause() != null ? error.getCause() : error;
        }
        return error;
    }
}
