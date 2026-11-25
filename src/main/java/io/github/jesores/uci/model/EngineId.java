package io.github.jesores.uci.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Metadatos de identificación reportados por un motor UCI mediante líneas {@code id name} e {@code id author}.
 *
 * <p>Esta clase es inmutable y segura para uso concurrente (<i>thread-safe</i>).
 *
 * @param name   nombre reportado por el motor, si estuvo presente durante el handshake
 * @param author autor reportado por el motor, si estuvo presente durante el handshake
 */
public record EngineId(Optional<String> name, Optional<String> author) {

    /**
     * Identificación vacía cuando el motor no envía metadatos {@code id}.
     */
    public static final EngineId EMPTY = new EngineId(Optional.empty(), Optional.empty());

    /**
     * Crea una instancia validando que los contenedores opcionales no sean nulos.
     *
     * @param name   nombre opcional del motor
     * @param author autor opcional del motor
     * @throws NullPointerException si {@code name} o {@code author} son {@code null}
     */
    public EngineId {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(author, "author must not be null");
    }
}
