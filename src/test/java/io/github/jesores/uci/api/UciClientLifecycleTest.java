package io.github.jesores.uci.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jesores.uci.model.EngineOption;
import io.github.jesores.uci.model.Info;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class UciClientLifecycleTest {

    private static final Path DUMMY_BIN = Path.of("/usr/bin/mock-uci-engine");

    @Test
    void synchronousLifecycleHandshakeOptionsAndCallbackThreadSafety() {
        InMemoryTransport transport = new InMemoryTransport();
        configureStandardHandshakeAndSearch(transport);

        UciConfig config = UciConfig.builder(DUMMY_BIN)
                .handshakeTimeout(Duration.ofSeconds(2))
                .defaultTimeout(Duration.ofSeconds(2))
                .closeTimeout(Duration.ofMillis(200))
                .build();

        try (UciClient client = DefaultUciClient.openWithTransport(transport, config)) {
            assertTrue(client.isOpen());
            assertEquals(Optional.of("MockFish 17"), client.engineId().name());
            assertEquals(Optional.of("UCI Team"), client.engineId().author());
            assertEquals(2, client.options().size());
            assertInstanceOf(EngineOption.Spin.class, client.options().get("Hash"));
            assertInstanceOf(EngineOption.Button.class, client.options().get("Clear Hash"));
            assertThrows(UnsupportedOperationException.class, () -> client.options().clear());

            client.ping();
            client.newGame();
            client.setOption("Hash", "64");
            client.triggerButton("Clear Hash");

            List<String> callbackThreadNames = new CopyOnWriteArrayList<>();
            AtomicBoolean allCallbacksDaemon = new AtomicBoolean(true);
            List<Info> streamedInfos = new ArrayList<>();

            PlayResult result = client.play(
                    Position.startpos("e2e4", "e7e5"),
                    Limit.depth(10),
                    info -> {
                        Thread current = Thread.currentThread();
                        callbackThreadNames.add(current.getName());
                        if (!current.isDaemon()) {
                            allCallbacksDaemon.set(false);
                        }
                        streamedInfos.add(info);
                    });

            assertEquals(Optional.of("g1f3"), result.bestMove());
            assertEquals(Optional.of("b8c6"), result.ponderMove());
            assertEquals(2, streamedInfos.size());
            assertEquals(2, result.info().size());
            assertEquals(Optional.of(Score.cp(32)), result.lastInfo().flatMap(Info::score));

            assertTrue(allCallbacksDaemon.get());
            for (String threadName : callbackThreadNames) {
                assertEquals("uci-callback", threadName);
                assertNotEquals("uci-reader", threadName);
            }
        }

        assertTrue(transport.isClosed());
        assertTrue(transport.sentHistory().contains("ucinewgame"));
        assertTrue(transport.sentHistory().contains("setoption name Hash value 64"));
        assertTrue(transport.sentHistory().contains("setoption name Clear Hash"));
        assertTrue(transport.sentHistory().contains("quit"));
    }

    private static void configureStandardHandshakeAndSearch(InMemoryTransport transport) {
        transport.onCommand(cmd -> {
            if ("uci".equals(cmd)) {
                transport.emitEngineLines(
                        "MockFish Engine Banner",
                        "id name MockFish 17",
                        "id author UCI Team",
                        "option name Hash type spin default 16 min 1 max 4096",
                        "option name Clear Hash type button",
                        "uciok");
            } else if ("isready".equals(cmd)) {
                transport.emitEngineLine("readyok");
            } else if (cmd.startsWith("go depth 10")) {
                transport.emitEngineLines(
                        "info depth 5 seldepth 8 score cp 20 nodes 1000 pv g1f3 b8c6",
                        "info depth 10 seldepth 14 score cp 32 nodes 8500 pv g1f3 b8c6 f1b5",
                        "bestmove g1f3 ponder b8c6");
            }
        });
    }
}
