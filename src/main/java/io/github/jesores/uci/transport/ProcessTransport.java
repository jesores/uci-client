package io.github.jesores.uci.transport;

import io.github.jesores.uci.api.UciEngineTerminatedException;
import io.github.jesores.uci.api.UciException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementación interna de {@link EngineTransport} respaldada por un subproceso del sistema operativo.
 */
public final class ProcessTransport implements EngineTransport {

    private static final Logger LOGGER = System.getLogger(ProcessTransport.class.getName());
    private static final Duration DEFAULT_CLOSE_GRACE = Duration.ofSeconds(2);

    private final Process process;
    private final BufferedWriter stdinWriter;
    private final BufferedReader stdoutReader;
    private final BufferedReader stderrReader;
    private final ProcessLifecycle lifecycle;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closedNotified = new AtomicBoolean(false);
    private final AtomicBoolean inputClosed = new AtomicBoolean(false);

    private ProcessTransport(Process process) {
        this.process = process;
        this.stdinWriter = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.stdoutReader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.stderrReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        this.lifecycle = new ProcessLifecycle(
                process,
                this::sendQuitAndCloseStdinOnShutdown,
                this::closeOutputStreams);
    }

    /**
     * Lanza el proceso ejecutable del motor UCI preparando sus tuberías UTF-8 y su hook de apagado.
     *
     * @param executable       ruta del binario del motor
     * @param arguments        argumentos de línea de comandos
     * @param workingDirectory directorio de trabajo opcional
     * @return transporte asociado al proceso recién iniciado
     */
    public static ProcessTransport launch(
            Path executable,
            List<String> arguments,
            Optional<Path> workingDirectory) {
        Objects.requireNonNull(executable, "executable must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(workingDirectory, "workingDirectory must not be null");

        List<String> command = new ArrayList<>(1 + arguments.size());
        command.add(executable.toString());
        command.addAll(arguments);

        ProcessBuilder builder = new ProcessBuilder(command);
        workingDirectory.ifPresent(dir -> builder.directory(dir.toFile()));
        builder.redirectErrorStream(false);

        Process startedProcess = null;
        try {
            startedProcess = builder.start();
            Process pidRef = startedProcess;
            LOGGER.log(Level.DEBUG, () -> "Started UCI subprocess pid=" + pidRef.pid() + " cmd=" + command);
            return new ProcessTransport(startedProcess);
        } catch (IOException ex) {
            if (startedProcess != null) {
                startedProcess.destroyForcibly();
            }
            throw new UciException("Failed to start UCI engine process: " + executable, ex);
        } catch (RuntimeException ex) {
            if (startedProcess != null) {
                startedProcess.destroyForcibly();
            }
            throw ex;
        }
    }

    @Override
    public void start(TransportListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Transport already started");
        }

        Thread readerThread = new Thread(() -> readStdoutLoop(listener), "uci-reader");
        readerThread.setDaemon(true);

        Thread stderrThread = new Thread(this::readStderrLoop, "uci-stderr");
        stderrThread.setDaemon(true);

        stderrThread.start();
        readerThread.start();
    }

    @Override
    public void sendLine(String line) {
        Objects.requireNonNull(line, "line must not be null");
        if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("UCI command must not contain line breaks");
        }
        if (inputClosed.get()) {
            throw new UciEngineTerminatedException("Engine stdin is closed");
        }

        writeLock.lock();
        try {
            if (inputClosed.get()) {
                throw new UciEngineTerminatedException("Engine stdin is closed");
            }
            LOGGER.log(Level.TRACE, () -> ">> " + line);
            stdinWriter.write(line);
            stdinWriter.write('\n');
            stdinWriter.flush();
        } catch (IOException ex) {
            throw new UciEngineTerminatedException("Failed to write line to UCI engine stdin", ex);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public CompletableFuture<Void> closeAsync(Duration gracePeriod) {
        return lifecycle.closeAsync(gracePeriod, this::closeStdin);
    }

    @Override
    public void close() {
        CompletableFuture<Void> future = closeAsync(DEFAULT_CLOSE_GRACE);
        try {
            future.get(DEFAULT_CLOSE_GRACE.toMillis() + 1000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            lifecycle.forceTerminate();
        } catch (ExecutionException | TimeoutException ex) {
            LOGGER.log(Level.WARNING, () -> "Timed out awaiting process cleanup: " + ex.getMessage());
            lifecycle.forceTerminate();
        }
    }

    boolean isShutdownHookRegistered() {
        return lifecycle.isShutdownHookRegistered();
    }

    boolean isProcessAlive() {
        return lifecycle.isProcessAlive();
    }

    void runShutdownHook() {
        lifecycle.runShutdownHook();
    }

    private void readStdoutLoop(TransportListener listener) {
        Throwable failure = null;
        try {
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                String currentLine = line;
                LOGGER.log(Level.TRACE, () -> "<< " + currentLine);
                listener.onLine(currentLine);
            }
        } catch (IOException ex) {
            if (!lifecycle.isCloseInitiated()) {
                failure = ex;
                LOGGER.log(Level.DEBUG, () -> "stdout read error: " + ex.getMessage());
            }
        } catch (RuntimeException ex) {
            failure = ex;
            LOGGER.log(Level.ERROR, () -> "Unexpected runtime failure in uci-reader: " + ex.getMessage());
        } finally {
            closeQuietly(stdoutReader, "stdout");
            if (closedNotified.compareAndSet(false, true)) {
                listener.onClosed(failure);
            }
        }
    }

    private void readStderrLoop() {
        try {
            String line;
            while ((line = stderrReader.readLine()) != null) {
                String errLine = line;
                LOGGER.log(Level.DEBUG, () -> "[stderr] " + errLine);
            }
        } catch (IOException ex) {
            if (!lifecycle.isCloseInitiated()) {
                LOGGER.log(Level.TRACE, () -> "stderr stream closed: " + ex.getMessage());
            }
        } finally {
            closeQuietly(stderrReader, "stderr");
        }
    }

    private void closeStdin() {
        if (inputClosed.compareAndSet(false, true)) {
            writeLock.lock();
            try {
                closeQuietly(stdinWriter, "stdin");
            } finally {
                writeLock.unlock();
            }
        }
    }

    private void sendQuitAndCloseStdinOnShutdown() {
        boolean locked = false;
        try {
            locked = writeLock.tryLock(50L, TimeUnit.MILLISECONDS);
            if (locked && inputClosed.compareAndSet(false, true)) {
                try {
                    stdinWriter.write("quit\n");
                    stdinWriter.flush();
                } catch (IOException ex) {
                    LOGGER.log(Level.TRACE, () -> "Ignored quit write failure in shutdown hook: " + ex.getMessage());
                }
                closeQuietly(stdinWriter, "stdin");
            } else if (!locked && inputClosed.compareAndSet(false, true)) {
                closeQuietly(process.getOutputStream(), "stdin-raw");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            closeQuietly(process.getOutputStream(), "stdin-raw");
        } finally {
            if (locked) {
                writeLock.unlock();
            }
        }
    }

    private void closeOutputStreams() {
        closeQuietly(stdoutReader, "stdout");
        closeQuietly(stderrReader, "stderr");
    }

    private static void closeQuietly(Closeable closeable, String streamName) {
        try {
            closeable.close();
        } catch (IOException ex) {
            LOGGER.log(Level.TRACE, () -> "Ignored IOException closing " + streamName + ": " + ex.getMessage());
        }
    }
}
