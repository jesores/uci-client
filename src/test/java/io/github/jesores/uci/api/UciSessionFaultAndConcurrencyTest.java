package io.github.jesores.uci.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jesores.uci.model.Limit;
import io.github.jesores.uci.model.PlayResult;
import io.github.jesores.uci.model.Position;
import io.github.jesores.uci.model.Score;
import io.github.jesores.uci.transport.InMemoryTransport;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UciSessionFaultAndConcurrencyTest {

    private static final Path DUMMY_BIN = Path.of("/usr/bin/mock-uci-engine");

    @Test
    void concurrentStopIsIdempotentSendsStopOnceAndRecoversToIdle() throws Exception {
        InMemoryTransport transport = new InMemoryTransport();
        CountDownLatch searchStartedLatch = new CountDownLatch(1);
        AtomicInteger stopCommandsReceived = new AtomicInteger(0);

        transport.onCommand(cmd -> {
            if ("uci".equals(cmd)) {
                transport.emitEngineLines("id name ConcurrentMock", "uciok");
            } else if ("isready".equals(cmd)) {
                transport.emitEngineLine("readyok");
            } else if ("go infinite".equals(cmd)) {
                transport.emitEngineLine("info depth 4 nodes 1200 pv d2d4 d7d5");
                searchStartedLatch.countDown();
            } else if ("stop".equals(cmd)) {
                int count = stopCommandsReceived.incrementAndGet();
                if (count == 1) {
                    transport.emitEngineLine("bestmove d2d4 ponder d7d5");
                }
            } else if ("go depth 2".equals(cmd)) {
                transport.emitEngineLine("bestmove c2c4");
            }
        });

        UciConfig config = UciConfig.builder(DUMMY_BIN)
                .handshakeTimeout(Duration.ofSeconds(2))
                .defaultTimeout(Duration.ofSeconds(2))
                .build();

        try (AsyncUciClient client = DefaultAsyncUciClient.openWithTransport(transport, config).join()) {
            CompletableFuture<PlayResult> searchFuture = client.play(Position.startpos(), Limit.infinite());
            assertTrue(searchStartedLatch.await(2, TimeUnit.SECONDS));

            assertThrows(
                    CompletionException.class,
                    () -> client.play(Position.startpos(), Limit.depth(1)).join());

            int callers = 12;
            CyclicBarrier barrier = new CyclicBarrier(callers);
            ExecutorService pool = Executors.newFixedThreadPool(callers);
            List<CompletableFuture<Void>> stopFutures = new ArrayList<>(callers);
            try {
                for (int i = 0; i < callers; i++) {
                    stopFutures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            barrier.await(2, TimeUnit.SECONDS);
                        } catch (Exception ex) {
                            throw new CompletionException(ex);
                        }
                        return client.stop();
                    }, pool).thenCompose(f -> f));
                }
                CompletableFuture.allOf(stopFutures.toArray(CompletableFuture[]::new))
                        .get(2, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }

            assertEquals(1, stopCommandsReceived.get());
            PlayResult firstResult = searchFuture.get(2, TimeUnit.SECONDS);
            assertEquals(Optional.of("d2d4"), firstResult.bestMove());

            PlayResult secondResult = client.play(Position.startpos(), Limit.depth(2)).get(2, TimeUnit.SECONDS);
            assertEquals(Optional.of("c2c4"), secondResult.bestMove());
        }
    }

    @Test
    void malformedInfoAndUnknownTokensDuringSearchDoNotBreakSession() {
        InMemoryTransport transport = new InMemoryTransport();
        transport.onCommand(cmd -> {
            switch (cmd) {
                case "uci" -> transport.emitEngineLine("uciok");
                case "isready" -> transport.emitEngineLine("readyok");
                case "go depth 5" -> transport.emitEngineLines(
                        "completely_unknown_banner_line 123",
                        "info depth broken_int mystery_token 42 nodes 900 score cp bad_cp",
                        "info depth 5 score mate 2 wdl 999 1 0 pv h5f7",
                        "bestmove h5f7");
                default -> {
                }
            }
        });

        try (UciClient client = DefaultUciClient.openWithTransport(transport, UciConfig.of(DUMMY_BIN))) {
            PlayResult result = client.play(Position.startpos(), Limit.depth(5));
            assertEquals(Optional.of("h5f7"), result.bestMove());
            assertEquals(2, result.info().size());
            assertTrue(result.info().get(0).depth().isEmpty());
            assertEquals(Optional.of(900L), result.info().get(0).nodes());
            assertEquals(Optional.of(Score.mate(2)), result.info().get(1).score());
        }
    }

    @Test
    void handshakeTimeoutCleansUpTransportAndThrowsUciTimeoutException() throws InterruptedException {
        InMemoryTransport transport = new InMemoryTransport();
        transport.onCommand(cmd -> {
            if ("uci".equals(cmd)) {
                transport.emitEngineLine("id name HungEngine");
            }
        });

        UciConfig fastTimeoutConfig = UciConfig.builder(DUMMY_BIN)
                .handshakeTimeout(Duration.ofMillis(60))
                .defaultTimeout(Duration.ofMillis(100))
                .closeTimeout(Duration.ofMillis(50))
                .build();

        assertThrows(
                UciTimeoutException.class,
                () -> DefaultUciClient.openWithTransport(transport, fastTimeoutConfig));
        assertTrue(transport.awaitClosed(Duration.ofSeconds(2)));
    }

    @Test
    void playTimeoutAutomaticallySendsStopAndAllowsCleanRecovery() throws InterruptedException {
        InMemoryTransport transport = new InMemoryTransport();
        CountDownLatch stopReceived = new CountDownLatch(1);

        transport.onCommand(cmd -> {
            if ("uci".equals(cmd)) {
                transport.emitEngineLine("uciok");
            } else if ("isready".equals(cmd)) {
                transport.emitEngineLine("readyok");
            } else if ("stop".equals(cmd)) {
                stopReceived.countDown();
                transport.emitEngineLine("bestmove e2e4");
            } else if ("go depth 1".equals(cmd)) {
                transport.emitEngineLine("bestmove a2a3");
            }
        });

        UciConfig config = UciConfig.builder(DUMMY_BIN)
                .handshakeTimeout(Duration.ofSeconds(2))
                .defaultTimeout(Duration.ofSeconds(2))
                .build();

        try (UciClient client = DefaultUciClient.openWithTransport(transport, config)) {
            assertThrows(
                    UciTimeoutException.class,
                    () -> client.play(Position.startpos(), Limit.infinite(), info -> {}, Duration.ofMillis(60)));

            assertTrue(stopReceived.await(2, TimeUnit.SECONDS));
            client.stop();

            PlayResult recovered = client.play(Position.startpos(), Limit.depth(1));
            assertEquals(Optional.of("a2a3"), recovered.bestMove());
        }
    }

    @Test
    void unexpectedEofMidSearchFailsPendingFuturesImmediately() throws Exception {
        InMemoryTransport transport = new InMemoryTransport();
        CountDownLatch searchStarted = new CountDownLatch(1);

        transport.onCommand(cmd -> {
            if ("uci".equals(cmd)) {
                transport.emitEngineLine("uciok");
            } else if ("isready".equals(cmd)) {
                transport.emitEngineLine("readyok");
            } else if ("go infinite".equals(cmd)) {
                transport.emitEngineLine("info depth 3 nodes 500");
                searchStarted.countDown();
            }
        });

        AsyncUciClient client = DefaultAsyncUciClient.openWithTransport(transport, UciConfig.of(DUMMY_BIN)).join();
        CompletableFuture<PlayResult> searchFuture = client.play(Position.startpos(), Limit.infinite());
        assertTrue(searchStarted.await(2, TimeUnit.SECONDS));

        transport.emitUnexpectedEof();

        CompletionException ex = assertThrows(CompletionException.class, searchFuture::join);
        assertInstanceOf(UciEngineTerminatedException.class, ex.getCause());
        assertTrue(transport.awaitClosed(Duration.ofSeconds(2)));
        assertFalse(client.isOpen());

        CompletionException afterCloseEx = assertThrows(
                CompletionException.class,
                () -> client.ping().join());
        assertInstanceOf(UciEngineTerminatedException.class, afterCloseEx.getCause());
    }
}
