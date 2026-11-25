package io.github.jesores.uci.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultado final de una búsqueda UCI culminada con el comando {@code bestmove} (inspirado en {@code chess.engine.PlayResult}).
 *
 * <p>Si el motor devuelve {@code bestmove (none)} o {@code bestmove 0000} (por ejemplo, en posición de mate o ahogado),
 * {@link #bestMove()} será {@link Optional#empty()}.
 *
 * <p>Esta clase es inmutable y segura para uso concurrente (<i>thread-safe</i>).
 *
 * @param bestMove   mejor jugada en notación UCI, o vacío si el motor reportó {@code (none)} / {@code 0000}
 * @param ponderMove jugada sugerida para ponderar ({@code ponder <move>}), si estuvo presente
 * @param info       historial inmutable de líneas {@link Info} recibidas durante la búsqueda
 */
public record PlayResult(
        Optional<String> bestMove,
        Optional<String> ponderMove,
        List<Info> info) {

    /**
     * Construye el resultado validando nulos y creando una copia inmutable de {@code info}.
     *
     * @param bestMove   mejor jugada opcional
     * @param ponderMove jugada de ponderación opcional
     * @param info       lista de instantáneas {@link Info} recolectadas
     * @throws NullPointerException si {@code bestMove}, {@code ponderMove}, {@code info} o algún elemento es {@code null}
     */
    public PlayResult {
        Objects.requireNonNull(bestMove, "bestMove must not be null");
        Objects.requireNonNull(ponderMove, "ponderMove must not be null");
        Objects.requireNonNull(info, "info must not be null");
        info = List.copyOf(info);
    }

    /**
     * Devuelve la última línea {@link Info} recibida antes de {@code bestmove}, si hubo alguna.
     *
     * @return última instancia de {@link Info} o {@link Optional#empty()}
     */
    public Optional<Info> lastInfo() {
        return info.isEmpty() ? Optional.empty() : Optional.of(info.getLast());
    }
}
