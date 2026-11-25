package io.github.jesores.uci.transport;

import io.github.jesores.uci.api.UciEngineTerminatedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Transporte determinista en memoria para pruebas unitarias sin depender de un motor externo instalado.
 * Ejecuta la entrega de líneas entrantes en un hilo daemon nombrado {@code uci-reader} igual que en producción.
 */
public final class InMemoryTransport implements EngineTransport {

    private sealed interface InboundEvent permits InboundEvent.Line, InboundEvent.Closed {
        record Line(String text) implements InboundEvent {}

        record Closed(Throwable cause) implements InboundEvent {}
    }

    private final BlockingQueue<InboundEvent> inboundQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> sentLinesQueue = new LinkedBlockingQueue<>();
    private final List<String> sentHistory = new CopyOnWriteArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CountDownLatch closedLatch = new CountDownLatch(1);
    private volatile Consumer<String> commandHandler = cmd -> {};

    public void onCommand(Consumer<String> handler) {
        this.commandHandler = Objects.requireNonNull(handler, "handler must not be null");
    }

    public void emitEngineLine(String line) {
        Objects.requireNonNull(line, "line must not be null");
        inboundQueue.add(new InboundEvent.Line(line));
    }

    public void emitEngineLines(String... lines) {
        for (String line : lines) {
            emitEngineLine(line);
        }
    }

    public void emitUnexpectedEof() {
        inboundQueue.add(new InboundEvent.Closed(null));
    }

    public void emitReadFailure(Throwable cause) {
        inboundQueue.add(new InboundEvent.Closed(Objects.requireNonNull(cause)));
    }

    public String awaitSentLine(Duration timeout) throws InterruptedException {
        String line = sentLinesQueue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (line == null) {
            throw new AssertionError("Timed out waiting for outbound UCI command after " + timeout);
        }
        return line;
    }

    public List<String> sentHistory() {
        return List.copyOf(new ArrayList<>(sentHistory));
    }

    public boolean awaitClosed(Duration timeout) throws InterruptedException {
        return closedLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void start(TransportListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("InMemoryTransport already started");
        }
        Thread readerThread = new Thread(() -> runReader(listener), "uci-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    @Override
    public void sendLine(String line) {
        Objects.requireNonNull(line, "line must not be null");
        if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Line break not allowed in UCI command");
        }
        if (closed.get()) {
            throw new UciEngineTerminatedException("InMemoryTransport is already closed");
        }
        sentHistory.add(line);
        sentLinesQueue.add(line);
        commandHandler.accept(line);
    }

    @Override
    public CompletableFuture<Void> closeAsync(Duration gracePeriod) {
        if (closed.compareAndSet(false, true)) {
            inboundQueue.add(new InboundEvent.Closed(null));
            closedLatch.countDown();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        closeAsync(Duration.ofMillis(100)).join();
    }

    private void runReader(TransportListener listener) {
        try {
            while (true) {
                InboundEvent event = inboundQueue.take();
                switch (event) {
                    case InboundEvent.Line(var text) -> listener.onLine(text);
                    case InboundEvent.Closed(var cause) -> {
                        listener.onClosed(cause);
                        return;
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            listener.onClosed(ex);
        }
    }
}
