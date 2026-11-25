package io.github.jesores.uci.transport;

import io.github.jesores.uci.api.UciEngineTerminatedException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class ProcessLifecycle {

    private static final Logger LOGGER = System.getLogger(ProcessLifecycle.class.getName());
    private static final long SHUTDOWN_HOOK_WAIT_MS = 200L;

    private final Process process;
    private final Runnable closeStreamsAction;
    private final Runnable shutdownStdinAction;
    private final Thread shutdownHook;
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<Void>> closeFutureRef = new AtomicReference<>();

    ProcessLifecycle(
            Process process,
            Runnable shutdownStdinAction,
            Runnable closeStreamsAction) {
        this.process = Objects.requireNonNull(process, "process must not be null");
        this.shutdownStdinAction = Objects.requireNonNull(shutdownStdinAction, "shutdownStdinAction must not be null");
        this.closeStreamsAction = Objects.requireNonNull(closeStreamsAction, "closeStreamsAction must not be null");
        this.shutdownHook = new Thread(this::runShutdownHook, "uci-shutdown-hook");
        this.shutdownHook.setDaemon(true);
        registerShutdownHook();
        this.process.onExit().whenComplete((proc, err) -> unregisterShutdownHook());
    }

    boolean isCloseInitiated() {
        return closeFutureRef.get() != null;
    }

    boolean isShutdownHookRegistered() {
        return shutdownHookRegistered.get();
    }

    boolean isProcessAlive() {
        return process.isAlive();
    }

    CompletableFuture<Void> closeAsync(Duration gracePeriod, Runnable closeStdinNormal) {
        Objects.requireNonNull(gracePeriod, "gracePeriod must not be null");
        CompletableFuture<Void> existing = closeFutureRef.get();
        if (existing != null) {
            return existing;
        }

        CompletableFuture<Void> created = new CompletableFuture<>();
        if (!closeFutureRef.compareAndSet(null, created)) {
            return closeFutureRef.get();
        }

        closeStdinNormal.run();

        long graceMillis = Math.max(1L, gracePeriod.toMillis());
        process.onExit()
                .copy()
                .orTimeout(graceMillis, TimeUnit.MILLISECONDS)
                .handle((proc, err) -> {
                    if (err != null && process.isAlive()) {
                        LOGGER.log(Level.DEBUG, () -> "Grace period expired; destroying UCI subprocess pid=" + process.pid());
                        process.destroy();
                        return process.onExit()
                                .copy()
                                .orTimeout(250L, TimeUnit.MILLISECONDS)
                                .handle((p2, err2) -> {
                                    if (err2 != null && process.isAlive()) {
                                        LOGGER.log(Level.WARNING, () -> "Forcibly destroying UCI subprocess pid=" + process.pid());
                                        process.destroyForcibly();
                                    }
                                    return (Void) null;
                                });
                    }
                    return CompletableFuture.<Void>completedFuture(null);
                })
                .thenCompose(f -> f)
                .whenComplete((ignored, error) -> {
                    unregisterShutdownHook();
                    closeStreamsAction.run();
                    if (error != null) {
                        LOGGER.log(Level.WARNING, () -> "Error while closing process transport: " + error.getMessage());
                    }
                    created.complete(null);
                });

        return created;
    }

    void forceTerminate() {
        unregisterShutdownHook();
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        closeStreamsAction.run();
    }

    void runShutdownHook() {
        shutdownHookRegistered.set(false);
        if (process.isAlive()) {
            shutdownStdinAction.run();
            awaitProcessExit(SHUTDOWN_HOOK_WAIT_MS);
            if (process.isAlive()) {
                process.destroy();
                awaitProcessExit(SHUTDOWN_HOOK_WAIT_MS);
            }
            if (process.isAlive()) {
                process.destroyForcibly();
                awaitProcessExit(SHUTDOWN_HOOK_WAIT_MS);
            }
        }
        closeStreamsAction.run();
    }

    private void registerShutdownHook() {
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            shutdownHookRegistered.set(true);
        } catch (IllegalStateException ex) {
            throw new UciEngineTerminatedException("Cannot launch UCI engine while JVM is shutting down", ex);
        }
    }

    private void unregisterShutdownHook() {
        if (shutdownHookRegistered.compareAndSet(true, false)) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ex) {
                LOGGER.log(Level.TRACE, () -> "JVM shutdown in progress; hook not removed: " + ex.getMessage());
            }
        }
    }

    private void awaitProcessExit(long timeoutMillis) {
        try {
            process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
