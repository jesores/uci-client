package io.github.jesores.uci.api;

import io.github.jesores.uci.model.EngineId;
import io.github.jesores.uci.model.EngineOption;
import io.github.jesores.uci.model.Info;
import io.github.jesores.uci.model.Limit;
import io.github.jesores.uci.model.PlayResult;
import io.github.jesores.uci.model.Position;
import io.github.jesores.uci.transport.EngineTransport;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

final class DefaultUciClient implements UciClient {

    private final DefaultAsyncUciClient asyncClient;

    private DefaultUciClient(DefaultAsyncUciClient asyncClient) {
        this.asyncClient = asyncClient;
    }

    static UciClient open(UciConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        EngineTransport transport = io.github.jesores.uci.transport.ProcessTransport.launch(
                config.executable(),
                config.arguments(),
                config.workingDirectory());
        return openWithTransport(transport, config);
    }

    static DefaultUciClient openWithTransport(EngineTransport transport, UciConfig config) {
        Objects.requireNonNull(transport, "transport must not be null");
        Objects.requireNonNull(config, "config must not be null");
        Duration safetyWait = config.handshakeTimeout().plusSeconds(2);
        DefaultAsyncUciClient initialized = await(
                DefaultAsyncUciClient.openWithTransport(transport, config),
                safetyWait,
                "handshake",
                () -> {});
        return new DefaultUciClient(initialized);
    }

    @Override
    public EngineId engineId() {
        return asyncClient.engineId();
    }

    @Override
    public Map<String, EngineOption> options() {
        return asyncClient.options();
    }

    @Override
    public boolean isOpen() {
        return asyncClient.isOpen();
    }

    @Override
    public void ping() {
        ping(asyncClient.config().defaultTimeout());
    }

    @Override
    public void ping(Duration timeout) {
        validateTimeout(timeout);
        await(asyncClient.ping(), timeout, "ping", () -> {});
    }

    @Override
    public void newGame() {
        newGame(asyncClient.config().defaultTimeout());
    }

    @Override
    public void newGame(Duration timeout) {
        validateTimeout(timeout);
        await(asyncClient.newGame(), timeout, "ucinewgame", () -> {});
    }

    @Override
    public void setOption(String name, String value) {
        await(asyncClient.setOption(name, value), asyncClient.config().defaultTimeout(), "setoption", () -> {});
    }

    @Override
    public void triggerButton(String name) {
        await(asyncClient.triggerButton(name), asyncClient.config().defaultTimeout(), "setoption", () -> {});
    }

    @Override
    public PlayResult play(Position position, Limit limit) {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(limit, "limit must not be null");
        return await(
                asyncClient.play(position, limit),
                asyncClient.config().defaultTimeout(),
                "play",
                asyncClient::onPlayTimeout);
    }

    @Override
    public PlayResult play(Position position, Limit limit, Consumer<Info> infoListener) {
        return play(position, limit, infoListener, asyncClient.config().defaultTimeout());
    }

    @Override
    public PlayResult play(
            Position position,
            Limit limit,
            Consumer<Info> infoListener,
            Duration timeout) {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(limit, "limit must not be null");
        Objects.requireNonNull(infoListener, "infoListener must not be null");
        validateTimeout(timeout);
        return await(
                asyncClient.play(position, limit, infoListener),
                timeout,
                "play",
                asyncClient::onPlayTimeout);
    }

    @Override
    public void stop() {
        await(asyncClient.stop(), asyncClient.config().defaultTimeout(), "stop", () -> {});
    }

    @Override
    public void close() {
        asyncClient.close();
    }

    private static void validateTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be > 0");
        }
    }

    private static <T> T await(
            CompletableFuture<T> future,
            Duration timeout,
            String operationName,
            Runnable onTimeout) {
        long waitMillis = Math.max(1L, timeout.toMillis());
        try {
            return future.get(waitMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            onTimeout.run();
            throw new UciTimeoutException(
                    "UCI operation '" + operationName + "' timed out after " + timeout,
                    ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            onTimeout.run();
            throw new UciException("Interrupted while waiting for UCI operation '" + operationName + "'", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeEx) {
                throw runtimeEx;
            }
            throw new UciException("UCI operation '" + operationName + "' failed", cause);
        }
    }
}
