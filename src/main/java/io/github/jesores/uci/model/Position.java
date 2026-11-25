package io.github.jesores.uci.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Representa la especificación de posición para el comando UCI {@code position}.
 *
 * <p>No valida reglas de ajedrez ni legalidad de jugadas; únicamente garantiza la integridad sintáctica
 * de las cadenas enviadas al protocolo UCI ({@code startpos} o {@code fen}, seguidas opcionalmente de
 * jugadas en notación UCI como {@code e2e4} o {@code e7e8q}).
 *
 * <p>Esta clase es inmutable y segura para uso concurrente (<i>thread-safe</i>).
 *
 * @param fen   cadena FEN opcional; si está vacía ({@link Optional#empty()}), se usa {@code startpos}
 * @param moves secuencia inmutable de jugadas en notación UCI aplicadas desde la posición base
 */
public record Position(Optional<String> fen, List<String> moves) {

    /**
     * Construye una especificación de posición con copias inmutables y validación de nulos y saltos de línea.
     *
     * @param fen   cadena FEN opcional ({@link Optional#empty()} representa {@code startpos})
     * @param moves lista de jugadas en notación UCI
     * @throws NullPointerException     si {@code fen}, {@code moves} o alguna jugada es {@code null}
     * @throws IllegalArgumentException si el FEN está en blanco/contiene saltos de línea o alguna jugada contiene espacios
     */
    public Position {
        Objects.requireNonNull(fen, "fen must not be null");
        Objects.requireNonNull(moves, "moves must not be null");
        fen.ifPresent(value -> {
            if (value.isBlank() || containsLineBreak(value)) {
                throw new IllegalArgumentException("fen must not be blank or contain line breaks");
            }
        });
        moves = List.copyOf(moves);
        for (String move : moves) {
            if (move.isBlank() || containsWhitespace(move)) {
                throw new IllegalArgumentException("UCI move must not be blank or contain whitespace: '" + move + "'");
            }
        }
    }

    /**
     * Crea la posición inicial estándar ({@code startpos}) sin jugadas adicionales.
     *
     * @return instancia de {@link Position} para {@code startpos}
     */
    public static Position startpos() {
        return new Position(Optional.empty(), List.of());
    }

    /**
     * Crea la posición inicial estándar seguida de una lista de jugadas UCI.
     *
     * @param moves jugadas en notación UCI (por ejemplo, {@code "e2e4"}, {@code "e7e5"})
     * @return instancia de {@link Position}
     * @throws NullPointerException     si {@code moves} o algún elemento es {@code null}
     * @throws IllegalArgumentException si alguna jugada está vacía o contiene espacios
     */
    public static Position startpos(List<String> moves) {
        return new Position(Optional.empty(), moves);
    }

    /**
     * Crea la posición inicial estándar seguida de jugadas UCI especificadas como argumentos variables.
     *
     * @param moves jugadas en notación UCI
     * @return instancia de {@link Position}
     * @throws NullPointerException     si {@code moves} o algún elemento es {@code null}
     * @throws IllegalArgumentException si alguna jugada está vacía o contiene espacios
     */
    public static Position startpos(String... moves) {
        Objects.requireNonNull(moves, "moves must not be null");
        return new Position(Optional.empty(), List.of(moves));
    }

    /**
     * Crea una posición a partir de una cadena FEN sin jugadas posteriores.
     *
     * @param fen cadena FEN no vacía
     * @return instancia de {@link Position}
     * @throws NullPointerException     si {@code fen} es {@code null}
     * @throws IllegalArgumentException si {@code fen} está en blanco o contiene saltos de línea
     */
    public static Position fen(String fen) {
        Objects.requireNonNull(fen, "fen must not be null");
        return new Position(Optional.of(fen), List.of());
    }

    /**
     * Crea una posición a partir de una cadena FEN y una secuencia de jugadas UCI.
     *
     * @param fen   cadena FEN no vacía
     * @param moves lista de jugadas en notación UCI
     * @return instancia de {@link Position}
     * @throws NullPointerException     si {@code fen}, {@code moves} o algún elemento es {@code null}
     * @throws IllegalArgumentException si {@code fen} o alguna jugada son sintácticamente inválidos para la línea UCI
     */
    public static Position fen(String fen, List<String> moves) {
        Objects.requireNonNull(fen, "fen must not be null");
        return new Position(Optional.of(fen), moves);
    }

    /**
     * Crea una posición a partir de una cadena FEN y jugadas UCI especificadas como argumentos variables.
     *
     * @param fen   cadena FEN no vacía
     * @param moves jugadas en notación UCI
     * @return instancia de {@link Position}
     * @throws NullPointerException     si {@code fen}, {@code moves} o algún elemento es {@code null}
     * @throws IllegalArgumentException si {@code fen} o alguna jugada son sintácticamente inválidos para la línea UCI
     */
    public static Position fen(String fen, String... moves) {
        Objects.requireNonNull(fen, "fen must not be null");
        Objects.requireNonNull(moves, "moves must not be null");
        return new Position(Optional.of(fen), List.of(moves));
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
