package io.github.jesores.uci.model;

import java.util.Objects;

/**
 * Evaluación reportada por el motor UCI dentro de una línea {@code info} (desde el punto de vista del bando al turno).
 *
 * <p>Jerarquía cerrada e inmutable, segura para uso concurrente (<i>thread-safe</i>).
 */
public sealed interface Score permits Score.Cp, Score.Mate {

    /**
     * Tipo de cota asociada a la evaluación reportada por el motor.
     */
    enum Bound {
        /** Evaluación exacta. */
        EXACT,
        /** Cota inferior ({@code lowerbound}). */
        LOWERBOUND,
        /** Cota superior ({@code upperbound}). */
        UPPERBOUND
    }

    /**
     * Devuelve el tipo de cota de esta evaluación.
     *
     * @return cota asociada ({@link Bound#EXACT}, {@link Bound#LOWERBOUND} o {@link Bound#UPPERBOUND})
     */
    Bound bound();

    /**
     * Crea una evaluación exacta en centipeones.
     *
     * @param value evaluación en centipeones
     * @return instancia de {@link Cp}
     */
    static Cp cp(int value) {
        return new Cp(value, Bound.EXACT);
    }

    /**
     * Crea una evaluación en centipeones con la cota indicada.
     *
     * @param value evaluación en centipeones
     * @param bound tipo de cota
     * @return instancia de {@link Cp}
     * @throws NullPointerException si {@code bound} es {@code null}
     */
    static Cp cp(int value, Bound bound) {
        return new Cp(value, bound);
    }

    /**
     * Crea una evaluación exacta de mate en jugadas.
     *
     * @param moves número de jugadas hasta el mate (positivo si da mate, negativo si recibe mate)
     * @return instancia de {@link Mate}
     */
    static Mate mate(int moves) {
        return new Mate(moves, Bound.EXACT);
    }

    /**
     * Crea una evaluación de mate en jugadas con la cota indicada.
     *
     * @param moves número de jugadas hasta el mate
     * @param bound tipo de cota
     * @return instancia de {@link Mate}
     * @throws NullPointerException si {@code bound} es {@code null}
     */
    static Mate mate(int moves, Bound bound) {
        return new Mate(moves, bound);
    }

    /**
     * Evaluación expresada en centipeones ({@code score cp <x>}).
     *
     * @param value valor en centipeones
     * @param bound cota asociada a la evaluación
     */
    record Cp(int value, Bound bound) implements Score {

        /**
         * Construye una evaluación en centipeones validando la cota.
         *
         * @param value valor en centipeones
         * @param bound cota asociada
         * @throws NullPointerException si {@code bound} es {@code null}
         */
        public Cp {
            Objects.requireNonNull(bound, "bound must not be null");
        }

        /**
         * Construye una evaluación exacta en centipeones.
         *
         * @param value valor en centipeones
         */
        public Cp(int value) {
            this(value, Bound.EXACT);
        }
    }

    /**
     * Evaluación de mate en un número determinado de jugadas ({@code score mate <y>}).
     *
     * @param moves jugadas para dar mate (positivo) o recibir mate (negativo)
     * @param bound cota asociada a la evaluación
     */
    record Mate(int moves, Bound bound) implements Score {

        /**
         * Construye una evaluación de mate validando la cota.
         *
         * @param moves jugadas hasta el mate
         * @param bound cota asociada
         * @throws NullPointerException si {@code bound} es {@code null}
         */
        public Mate {
            Objects.requireNonNull(bound, "bound must not be null");
        }

        /**
         * Construye una evaluación exacta de mate.
         *
         * @param moves jugadas hasta el mate
         */
        public Mate(int moves) {
            this(moves, Bound.EXACT);
        }
    }
}
