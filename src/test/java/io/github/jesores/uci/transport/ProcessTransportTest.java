package io.github.jesores.uci.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jesores.uci.api.UciClient;
import io.github.jesores.uci.api.UciConfig;
import io.github.jesores.uci.api.UciTimeoutException;
import io.github.jesores.uci.model.Limit;
import io.github.jesores.uci.model.PlayResult;
import io.github.jesores.uci.model.Position;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ProcessTransportTest {

    private static final Path SH_PATH = Path.of("/bin/sh");

    @Test
    void communicatesWithPosixSubprocessDrainingStderrAndCleanlyClosing() {
        Assumptions.assumeTrue(Files.isExecutable(SH_PATH), "/bin/sh is required for subprocess test");

        String script = """
                while IFS= read -r line; do
                  case "$line" in
                    uci)
                      echo "debug stderr startup" >&2
                      echo "id name ShellEngine 1.0"
                      echo "uciok"
                      ;;
                    isready)
                      echo "readyok"
                      ;;
                    go*)
                      echo "info depth 1 score cp 15 pv e2e4"
                      echo "bestmove e2e4"
                      ;;
                    quit)
                      exit 0
                      ;;
                  esac
                done
                """;

        UciConfig config = UciConfig.builder(SH_PATH)
                .arguments(List.of("-c", script))
                .handshakeTimeout(Duration.ofSeconds(3))
                .defaultTimeout(Duration.ofSeconds(3))
                .closeTimeout(Duration.ofMillis(500))
                .build();

        try (UciClient client = UciClient.open(config)) {
            assertEquals(Optional.of("ShellEngine 1.0"), client.engineId().name());
            PlayResult result = client.play(Position.startpos(), Limit.depth(1));
            assertEquals(Optional.of("e2e4"), result.bestMove());
        }
    }

    @Test
    void forciblyTerminatesUnresponsiveSubprocessWhenHandshakeTimesOut() {
        Assumptions.assumeTrue(Files.isExecutable(SH_PATH), "/bin/sh is required for subprocess test");

        String unresponsiveScript = "trap '' TERM; while IFS= read -r line; do :; done";

        UciConfig config = UciConfig.builder(SH_PATH)
                .arguments(List.of("-c", unresponsiveScript))
                .handshakeTimeout(Duration.ofMillis(100))
                .defaultTimeout(Duration.ofMillis(100))
                .closeTimeout(Duration.ofMillis(80))
                .build();

        assertThrows(UciTimeoutException.class, () -> UciClient.open(config));
    }

    @Test
    void closeUnregistersShutdownHookToAvoidMemoryRetention() {
        Assumptions.assumeTrue(Files.isExecutable(SH_PATH), "/bin/sh is required for subprocess test");

        ProcessTransport transport = ProcessTransport.launch(
                SH_PATH,
                List.of("-c", "while IFS= read -r line; do [ \"$line\" = quit ] && exit 0; done"),
                Optional.empty());
        try {
            assertTrue(transport.isProcessAlive());
            assertTrue(transport.isShutdownHookRegistered());
            transport.sendLine("quit");
        } finally {
            transport.close();
        }

        assertFalse(transport.isProcessAlive());
        assertFalse(transport.isShutdownHookRegistered());
    }

    @Test
    void shutdownHookKillsStubbornSubprocessIgnoringEofAndSigterm() throws InterruptedException {
        Assumptions.assumeTrue(Files.isExecutable(SH_PATH), "/bin/sh is required for subprocess test");

        CountDownLatch readyLatch = new CountDownLatch(1);
        ProcessTransport transport = ProcessTransport.launch(
                SH_PATH,
                List.of("-c", "trap '' TERM; echo ready; while :; do read -r line || :; done"),
                Optional.empty());
        try {
            transport.start(new TransportListener() {
                @Override
                public void onLine(String line) {
                    if ("ready".equals(line)) {
                        readyLatch.countDown();
                    }
                }

                @Override
                public void onClosed(Throwable cause) {}
            });

            assertTrue(readyLatch.await(2, TimeUnit.SECONDS));
            assertTrue(transport.isProcessAlive());
            assertTrue(transport.isShutdownHookRegistered());

            transport.runShutdownHook();

            assertFalse(transport.isProcessAlive());
        } finally {
            transport.close();
        }
    }
}
