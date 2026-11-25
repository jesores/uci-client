package io.github.jesores.uci.protocol;

import io.github.jesores.uci.model.Info;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

final class CallbackDispatcher {

    private static final Logger LOGGER = System.getLogger(CallbackDispatcher.class.getName());

    private final ExecutorService executor;

    CallbackDispatcher() {
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "uci-callback");
            thread.setDaemon(true);
            return thread;
        });
    }

    void dispatch(Runnable task, Runnable fallbackIfRejected) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ex) {
            fallbackIfRejected.run();
        }
    }

    void dispatchInfo(Consumer<Info> listener, Info info) {
        dispatch(
                () -> {
                    try {
                        listener.accept(info);
                    } catch (RuntimeException ex) {
                        LOGGER.log(Level.WARNING, () -> "Exception thrown by Info listener: " + ex.getMessage());
                    }
                },
                () -> {});
    }

    void completeVoid(CompletableFuture<Void> future) {
        Runnable complete = () -> future.complete(null);
        dispatch(complete, complete);
    }

    void failAll(List<CompletableFuture<?>> futures, Throwable error) {
        if (futures.isEmpty()) {
            return;
        }
        Runnable failTask = () -> {
            for (CompletableFuture<?> future : futures) {
                future.completeExceptionally(error);
            }
        };
        dispatch(failTask, failTask);
    }

    void shutdown() {
        executor.shutdown();
    }
}
