package io.github.jesores.uci.api;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuración inmutable para lanzar y operar un cliente de motor UCI.
 *
 * <p>Esta clase es inmutable y segura para uso concurrente (<i>thread-safe</i>).
 * Su {@link Builder} asociado no es seguro para uso concurrente.
 *
 * @param executable       ruta al binario ejecutable del motor UCI
 * @param arguments        argumentos adicionales de línea de comandos pasados al proceso
 * @param workingDirectory directorio de trabajo opcional del subproceso
 * @param handshakeTimeout tiempo máximo para completar el handshake inicial ({@code uciok} + {@code readyok})
 * @param defaultTimeout   tiempo máximo por defecto para operaciones síncronas
 * @param closeTimeout     tiempo de gracia tras enviar {@code quit} antes de terminar forzosamente el subproceso
 */
public record UciConfig(
        Path executable,
        List<String> arguments,
        Optional<Path> workingDirectory,
        Duration handshakeTimeout,
        Duration defaultTimeout,
        Duration closeTimeout) {

    /** Tiempo de espera por defecto para el handshake inicial (10 segundos). */
    public static final Duration DEFAULT_HANDSHAKE_TIMEOUT = Duration.ofSeconds(10);

    /** Tiempo de espera por defecto para operaciones síncronas (30 segundos). */
    public static final Duration DEFAULT_OPERATION_TIMEOUT = Duration.ofSeconds(30);

    /** Tiempo de gracia por defecto para el cierre del motor (2 segundos). */
    public static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Construye una configuración validando que ningún parámetro sea nulo y que los timeouts sean positivos.
     *
     * @param executable       ruta al ejecutable del motor
     * @param arguments        lista de argumentos del proceso
     * @param workingDirectory directorio de trabajo opcional
     * @param handshakeTimeout timeout para handshake
     * @param defaultTimeout   timeout por defecto para llamadas síncronas
     * @param closeTimeout     timeout de cierre del proceso
     * @throws NullPointerException     si algún parámetro o argumento de la lista es {@code null}
     * @throws IllegalArgumentException si algún timeout es cero o negativo
     */
    public UciConfig {
        Objects.requireNonNull(executable, "executable must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(workingDirectory, "workingDirectory must not be null");
        Objects.requireNonNull(handshakeTimeout, "handshakeTimeout must not be null");
        Objects.requireNonNull(defaultTimeout, "defaultTimeout must not be null");
        Objects.requireNonNull(closeTimeout, "closeTimeout must not be null");

        requirePositive(handshakeTimeout, "handshakeTimeout");
        requirePositive(defaultTimeout, "defaultTimeout");
        requirePositive(closeTimeout, "closeTimeout");

        arguments = List.copyOf(arguments);
    }

    /**
     * Crea una configuración con valores de timeout predeterminados para el ejecutable indicado.
     *
     * @param executable ruta al binario del motor UCI
     * @return instancia inmutable de {@link UciConfig}
     * @throws NullPointerException si {@code executable} es {@code null}
     */
    public static UciConfig of(Path executable) {
        return builder(executable).build();
    }

    /**
     * Crea un nuevo {@link Builder} para configurar el cliente UCI.
     *
     * @param executable ruta al binario ejecutable del motor
     * @return nuevo {@link Builder}
     * @throws NullPointerException si {@code executable} es {@code null}
     */
    public static Builder builder(Path executable) {
        return new Builder(executable);
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
    }

    /**
     * Constructor fluido para {@link UciConfig}. No es seguro para uso concurrente.
     */
    public static final class Builder {
        private final Path executable;
        private List<String> arguments = List.of();
        private Optional<Path> workingDirectory = Optional.empty();
        private Duration handshakeTimeout = DEFAULT_HANDSHAKE_TIMEOUT;
        private Duration defaultTimeout = DEFAULT_OPERATION_TIMEOUT;
        private Duration closeTimeout = DEFAULT_CLOSE_TIMEOUT;

        private Builder(Path executable) {
            this.executable = Objects.requireNonNull(executable, "executable must not be null");
        }

        /**
         * Establece los argumentos de línea de comandos del proceso.
         *
         * @param arguments lista de argumentos
         * @return este builder
         * @throws NullPointerException si {@code arguments} o algún elemento es {@code null}
         */
        public Builder arguments(List<String> arguments) {
            this.arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments must not be null"));
            return this;
        }

        /**
         * Establece el directorio de trabajo del subproceso.
         *
         * @param workingDirectory ruta del directorio de trabajo
         * @return este builder
         * @throws NullPointerException si {@code workingDirectory} es {@code null}
         */
        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = Optional.of(
                    Objects.requireNonNull(workingDirectory, "workingDirectory must not be null"));
            return this;
        }

        /**
         * Establece el tiempo máximo para completar el handshake UCI.
         *
         * @param handshakeTimeout duración estrictamente positiva
         * @return este builder
         * @throws NullPointerException si {@code handshakeTimeout} es {@code null}
         */
        public Builder handshakeTimeout(Duration handshakeTimeout) {
            this.handshakeTimeout = Objects.requireNonNull(handshakeTimeout, "handshakeTimeout must not be null");
            return this;
        }

        /**
         * Establece el timeout por defecto para operaciones síncronas.
         *
         * @param defaultTimeout duración estrictamente positiva
         * @return este builder
         * @throws NullPointerException si {@code defaultTimeout} es {@code null}
         */
        public Builder defaultTimeout(Duration defaultTimeout) {
            this.defaultTimeout = Objects.requireNonNull(defaultTimeout, "defaultTimeout must not be null");
            return this;
        }

        /**
         * Establece el tiempo de gracia al cerrar el motor antes de destruir el proceso.
         *
         * @param closeTimeout duración estrictamente positiva
         * @return este builder
         * @throws NullPointerException si {@code closeTimeout} es {@code null}
         */
        public Builder closeTimeout(Duration closeTimeout) {
            this.closeTimeout = Objects.requireNonNull(closeTimeout, "closeTimeout must not be null");
            return this;
        }

        /**
         * Construye la configuración inmutable.
         *
         * @return instancia de {@link UciConfig}
         */
        public UciConfig build() {
            return new UciConfig(
                    executable,
                    arguments,
                    workingDirectory,
                    handshakeTimeout,
                    defaultTimeout,
                    closeTimeout);
        }
    }
}
