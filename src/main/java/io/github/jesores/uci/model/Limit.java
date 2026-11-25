package io.github.jesores.uci.model;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Parámetros de control de búsqueda para el comando UCI {@code go} (inspirado en {@code chess.engine.Limit}).
 *
 * <p>Esta clase es inmutable y segura para uso concurrente (<i>thread-safe</i>).
 * El constructor fluido {@link Builder} no es <i>thread-safe</i>.
 *
 * @param depth          profundidad máxima en plies ({@code depth <x>})
 * @param nodes          número máximo de nodos a evaluar ({@code nodes <x>})
 * @param mate           búsqueda de mate en {@code x} jugadas ({@code mate <x>})
 * @param moveTime       tiempo exacto dedicado a la jugada ({@code movetime <x>})
 * @param whiteTime      tiempo restante de blancas ({@code wtime <x>})
 * @param blackTime      tiempo restante de negras ({@code btime <x>})
 * @param whiteIncrement incremento por jugada de blancas ({@code winc <x>})
 * @param blackIncrement incremento por jugada de negras ({@code binc <x>})
 * @param movesToGo      jugadas restantes para el siguiente control de tiempo ({@code movestogo <x>})
 * @param searchMoves    subconjunto restringido de jugadas UCI a analizar ({@code searchmoves ...})
 * @param ponder         activar búsqueda en modo {@code ponder}
 * @param isInfinite     búsqueda indefinida hasta recibir {@code stop} ({@code infinite})
 */
public record Limit(
        Optional<Integer> depth,
        Optional<Long> nodes,
        Optional<Integer> mate,
        Optional<Duration> moveTime,
        Optional<Duration> whiteTime,
        Optional<Duration> blackTime,
        Optional<Duration> whiteIncrement,
        Optional<Duration> blackIncrement,
        Optional<Integer> movesToGo,
        List<String> searchMoves,
        boolean ponder,
        boolean isInfinite) {

    /**
     * Construye un límite de búsqueda validando nulos, valores no negativos y formato de jugadas.
     *
     * @param depth          profundidad máxima opcional
     * @param nodes          máximo de nodos opcional
     * @param mate           mate en N jugadas opcional
     * @param moveTime       tiempo por jugada opcional
     * @param whiteTime      tiempo de blancas opcional
     * @param blackTime      tiempo de negras opcional
     * @param whiteIncrement incremento de blancas opcional
     * @param blackIncrement incremento de negras opcional
     * @param movesToGo      jugadas para el control opcional
     * @param searchMoves    lista de jugadas raíz a considerar
     * @param ponder         indicador de ponderación
     * @param isInfinite     indicador de análisis infinito
     * @throws NullPointerException     si cualquier contenedor {@link Optional}, {@code searchMoves} o jugada es {@code null}
     * @throws IllegalArgumentException si algún parámetro numérico o duración es inválido
     */
    public Limit {
        Objects.requireNonNull(depth, "depth must not be null");
        Objects.requireNonNull(nodes, "nodes must not be null");
        Objects.requireNonNull(mate, "mate must not be null");
        Objects.requireNonNull(moveTime, "moveTime must not be null");
        Objects.requireNonNull(whiteTime, "whiteTime must not be null");
        Objects.requireNonNull(blackTime, "blackTime must not be null");
        Objects.requireNonNull(whiteIncrement, "whiteIncrement must not be null");
        Objects.requireNonNull(blackIncrement, "blackIncrement must not be null");
        Objects.requireNonNull(movesToGo, "movesToGo must not be null");
        Objects.requireNonNull(searchMoves, "searchMoves must not be null");

        depth.ifPresent(d -> requireNonNegative(d, "depth"));
        nodes.ifPresent(n -> requireNonNegative(n, "nodes"));
        mate.ifPresent(m -> requireNonNegative(m, "mate"));
        movesToGo.ifPresent(m -> {
            if (m <= 0) {
                throw new IllegalArgumentException("movesToGo must be > 0");
            }
        });
        moveTime.ifPresent(d -> requireNonNegativeDuration(d, "moveTime"));
        whiteIncrement.ifPresent(d -> requireNonNegativeDuration(d, "whiteIncrement"));
        blackIncrement.ifPresent(d -> requireNonNegativeDuration(d, "blackIncrement"));

        searchMoves = List.copyOf(searchMoves);
        for (String move : searchMoves) {
            if (move.isBlank() || containsWhitespace(move)) {
                throw new IllegalArgumentException("searchMove must not be blank or contain whitespace: '" + move + "'");
            }
        }
    }

    /**
     * Crea un límite por profundidad fija.
     *
     * @param depth profundidad en plies ({@code >= 0})
     * @return instancia de {@link Limit}
     */
    public static Limit depth(int depth) {
        return builder().depth(depth).build();
    }

    /**
     * Crea un límite por cantidad máxima de nodos.
     *
     * @param nodes número de nodos ({@code >= 0})
     * @return instancia de {@link Limit}
     */
    public static Limit nodes(long nodes) {
        return builder().nodes(nodes).build();
    }

    /**
     * Crea un límite para buscar mate en {@code moves} jugadas.
     *
     * @param moves jugadas hasta el mate ({@code >= 0})
     * @return instancia de {@link Limit}
     */
    public static Limit mate(int moves) {
        return builder().mate(moves).build();
    }

    /**
     * Crea un límite por tiempo fijo de análisis.
     *
     * @param duration duración de búsqueda no negativa
     * @return instancia de {@link Limit}
     * @throws NullPointerException si {@code duration} es {@code null}
     */
    public static Limit moveTime(Duration duration) {
        return builder().moveTime(duration).build();
    }

    /**
     * Crea un límite con relojes e incrementos para ambos bandos.
     *
     * @param whiteTime      tiempo restante de blancas
     * @param blackTime      tiempo restante de negras
     * @param whiteIncrement incremento de blancas
     * @param blackIncrement incremento de negras
     * @return instancia de {@link Limit}
     * @throws NullPointerException si alguna duración es {@code null}
     */
    public static Limit clock(
            Duration whiteTime,
            Duration blackTime,
            Duration whiteIncrement,
            Duration blackIncrement) {
        return builder()
                .whiteTime(whiteTime)
                .blackTime(blackTime)
                .whiteIncrement(whiteIncrement)
                .blackIncrement(blackIncrement)
                .build();
    }

    /**
     * Crea un límite de análisis infinito ({@code go infinite}).
     *
     * @return instancia de {@link Limit}
     */
    public static Limit infinite() {
        return builder().infinite(true).build();
    }

    /**
     * Devuelve un nuevo constructor fluido para combinar múltiples límites de búsqueda.
     *
     * @return nuevo {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be >= 0");
        }
    }

    private static void requireNonNegativeDuration(Duration duration, String field) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Constructor fluido para instancias de {@link Limit}. No es seguro para uso concurrente.
     */
    public static final class Builder {
        private Optional<Integer> depth = Optional.empty();
        private Optional<Long> nodes = Optional.empty();
        private Optional<Integer> mate = Optional.empty();
        private Optional<Duration> moveTime = Optional.empty();
        private Optional<Duration> whiteTime = Optional.empty();
        private Optional<Duration> blackTime = Optional.empty();
        private Optional<Duration> whiteIncrement = Optional.empty();
        private Optional<Duration> blackIncrement = Optional.empty();
        private Optional<Integer> movesToGo = Optional.empty();
        private List<String> searchMoves = List.of();
        private boolean ponder;
        private boolean infinite;

        private Builder() {}

        /**
         * Define la profundidad máxima en plies.
         *
         * @param depth profundidad ({@code >= 0})
         * @return este builder
         */
        public Builder depth(int depth) {
            this.depth = Optional.of(depth);
            return this;
        }

        /**
         * Define el máximo de nodos a evaluar.
         *
         * @param nodes cantidad de nodos ({@code >= 0})
         * @return este builder
         */
        public Builder nodes(long nodes) {
            this.nodes = Optional.of(nodes);
            return this;
        }

        /**
         * Define la búsqueda de mate en {@code moves} jugadas.
         *
         * @param moves jugadas ({@code >= 0})
         * @return este builder
         */
        public Builder mate(int moves) {
            this.mate = Optional.of(moves);
            return this;
        }

        /**
         * Define el tiempo fijo por jugada.
         *
         * @param moveTime duración no negativa
         * @return este builder
         * @throws NullPointerException si {@code moveTime} es {@code null}
         */
        public Builder moveTime(Duration moveTime) {
            this.moveTime = Optional.of(Objects.requireNonNull(moveTime, "moveTime must not be null"));
            return this;
        }

        /**
         * Define el tiempo restante en el reloj de las blancas (se permite negativo en apuros de reloj).
         *
         * @param whiteTime tiempo de blancas
         * @return este builder
         * @throws NullPointerException si {@code whiteTime} es {@code null}
         */
        public Builder whiteTime(Duration whiteTime) {
            this.whiteTime = Optional.of(Objects.requireNonNull(whiteTime, "whiteTime must not be null"));
            return this;
        }

        /**
         * Define el tiempo restante en el reloj de las negras (se permite negativo en apuros de reloj).
         *
         * @param blackTime tiempo de negras
         * @return este builder
         * @throws NullPointerException si {@code blackTime} es {@code null}
         */
        public Builder blackTime(Duration blackTime) {
            this.blackTime = Optional.of(Objects.requireNonNull(blackTime, "blackTime must not be null"));
            return this;
        }

        /**
         * Define el incremento por jugada de las blancas.
         *
         * @param whiteIncrement incremento no negativo
         * @return este builder
         * @throws NullPointerException si {@code whiteIncrement} es {@code null}
         */
        public Builder whiteIncrement(Duration whiteIncrement) {
            this.whiteIncrement = Optional.of(Objects.requireNonNull(whiteIncrement, "whiteIncrement must not be null"));
            return this;
        }

        /**
         * Define el incremento por jugada de las negras.
         *
         * @param blackIncrement incremento no negativo
         * @return este builder
         * @throws NullPointerException si {@code blackIncrement} es {@code null}
         */
        public Builder blackIncrement(Duration blackIncrement) {
            this.blackIncrement = Optional.of(Objects.requireNonNull(blackIncrement, "blackIncrement must not be null"));
            return this;
        }

        /**
         * Define las jugadas restantes hasta el próximo control de tiempo.
         *
         * @param movesToGo jugadas restantes ({@code > 0})
         * @return este builder
         */
        public Builder movesToGo(int movesToGo) {
            this.movesToGo = Optional.of(movesToGo);
            return this;
        }

        /**
         * Restringe la búsqueda al subconjunto indicado de jugadas UCI.
         *
         * @param searchMoves lista de jugadas UCI
         * @return este builder
         * @throws NullPointerException si {@code searchMoves} o algún elemento es {@code null}
         */
        public Builder searchMoves(List<String> searchMoves) {
            this.searchMoves = List.copyOf(Objects.requireNonNull(searchMoves, "searchMoves must not be null"));
            return this;
        }

        /**
         * Activa o desactiva el modo {@code ponder}.
         *
         * @param ponder {@code true} para activar {@code ponder}
         * @return este builder
         */
        public Builder ponder(boolean ponder) {
            this.ponder = ponder;
            return this;
        }

        /**
         * Activa o desactiva el modo {@code infinite}.
         *
         * @param infinite {@code true} para activar búsqueda infinita
         * @return este builder
         */
        public Builder infinite(boolean infinite) {
            this.infinite = infinite;
            return this;
        }

        /**
         * Construye la instancia inmutable de {@link Limit}.
         *
         * @return nueva instancia de {@link Limit}
         */
        public Limit build() {
            return new Limit(
                    depth,
                    nodes,
                    mate,
                    moveTime,
                    whiteTime,
                    blackTime,
                    whiteIncrement,
                    blackIncrement,
                    movesToGo,
                    searchMoves,
                    ponder,
                    infinite);
        }
    }
}
