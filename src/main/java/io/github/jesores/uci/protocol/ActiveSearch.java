package io.github.jesores.uci.protocol;

import io.github.jesores.uci.model.Info;
import io.github.jesores.uci.model.PlayResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class ActiveSearch {

    private final Consumer<Info> infoListener;
    private final List<Info> infos = new ArrayList<>();
    private final CompletableFuture<PlayResult> userFuture = new CompletableFuture<>();
    private final CompletableFuture<Void> terminationFuture = new CompletableFuture<>();
    private boolean abandoned;

    ActiveSearch(Consumer<Info> infoListener) {
        this.infoListener = infoListener;
    }

    CompletableFuture<PlayResult> userFuture() {
        return userFuture;
    }

    CompletableFuture<Void> terminationFuture() {
        return terminationFuture;
    }

    boolean isAbandoned() {
        return abandoned;
    }

    void markAbandoned() {
        this.abandoned = true;
    }

    Consumer<Info> recordInfoAndGetListener(Info info) {
        if (abandoned) {
            return null;
        }
        infos.add(info);
        return infoListener;
    }

    PlayResult buildResult(Optional<String> bestMove, Optional<String> ponderMove) {
        return new PlayResult(bestMove, ponderMove, infos);
    }

    Runnable completionTask(PlayResult result) {
        return () -> {
            if (!abandoned) {
                userFuture.complete(result);
            }
            terminationFuture.complete(null);
        };
    }

    void collectPendingFutures(List<CompletableFuture<?>> target) {
        target.add(userFuture);
        target.add(terminationFuture);
    }
}
