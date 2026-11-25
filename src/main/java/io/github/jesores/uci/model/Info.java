package io.github.jesores.uci.model;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Instantánea estructurada de una línea {@code info} emitida por el motor durante el análisis.
 *
 * <p>Esta clase es inmutable y segura para uso concurrente (<i>thread-safe</i>).
 *
 * @param depth          profundidad alcanzada en plies ({@code depth})
 * @param seldepth       profundidad selectiva en plies ({@code seldepth})
 * @param multipv        índice de variante principal en modo MultiPV ({@code multipv})
 * @param score          evaluación asociada ({@code score cp} / {@code score mate})
 * @param nodes          nodos buscados ({@code nodes})
 * @param nps            nodos por segundo ({@code nps})
 * @param hashfull       ocupación de tabla hash en por mil ({@code hashfull})
 * @param tbhits         accesos a tablas de finales ({@code tbhits})
 * @param time           tiempo transcurrido de búsqueda ({@code time})
 * @param pv             variante principal como lista inmutable de jugadas UCI ({@code pv})
 * @param currmove       jugada raíz evaluada actualmente ({@code currmove})
 * @param currmovenumber índice 1-based de la jugada actual ({@code currmovenumber})
 * @param string         mensaje textual libre enviado por el motor ({@code string})
 */
public record Info(
        Optional<Integer> depth,
        Optional<Integer> seldepth,
        Optional<Integer> multipv,
        Optional<Score> score,
        Optional<Long> nodes,
        Optional<Long> nps,
        Optional<Integer> hashfull,
        Optional<Long> tbhits,
        Optional<Duration> time,
        List<String> pv,
        Optional<String> currmove,
        Optional<Integer> currmovenumber,
        Optional<String> string) {

    /**
     * Construye un registro {@link Info} validando nulos y copiando defensivamente la variante principal.
     *
     * @param depth          profundidad opcional
     * @param seldepth       profundidad selectiva opcional
     * @param multipv        índice multipv opcional
     * @param score          evaluación opcional
     * @param nodes          nodos opcionales
     * @param nps            nodos por segundo opcionales
     * @param hashfull       ocupación hash opcional
     * @param tbhits         accesos a tablebases opcionales
     * @param time           tiempo de búsqueda opcional
     * @param pv             secuencia de jugadas de la variante principal
     * @param currmove       jugada actual opcional
     * @param currmovenumber número de jugada actual opcional
     * @param string         texto informativo opcional
     * @throws NullPointerException si cualquier contenedor {@link Optional}, {@code pv} o elemento de {@code pv} es {@code null}
     */
    public Info {
        Objects.requireNonNull(depth, "depth must not be null");
        Objects.requireNonNull(seldepth, "seldepth must not be null");
        Objects.requireNonNull(multipv, "multipv must not be null");
        Objects.requireNonNull(score, "score must not be null");
        Objects.requireNonNull(nodes, "nodes must not be null");
        Objects.requireNonNull(nps, "nps must not be null");
        Objects.requireNonNull(hashfull, "hashfull must not be null");
        Objects.requireNonNull(tbhits, "tbhits must not be null");
        Objects.requireNonNull(time, "time must not be null");
        Objects.requireNonNull(pv, "pv must not be null");
        Objects.requireNonNull(currmove, "currmove must not be null");
        Objects.requireNonNull(currmovenumber, "currmovenumber must not be null");
        Objects.requireNonNull(string, "string must not be null");
        pv = List.copyOf(pv);
    }

    /**
     * Crea un nuevo constructor fluido para {@link Info}.
     *
     * @return nuevo {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Constructor fluido para {@link Info}. No es seguro para uso concurrente.
     */
    public static final class Builder {
        private Optional<Integer> depth = Optional.empty();
        private Optional<Integer> seldepth = Optional.empty();
        private Optional<Integer> multipv = Optional.empty();
        private Optional<Score> score = Optional.empty();
        private Optional<Long> nodes = Optional.empty();
        private Optional<Long> nps = Optional.empty();
        private Optional<Integer> hashfull = Optional.empty();
        private Optional<Long> tbhits = Optional.empty();
        private Optional<Duration> time = Optional.empty();
        private List<String> pv = List.of();
        private Optional<String> currmove = Optional.empty();
        private Optional<Integer> currmovenumber = Optional.empty();
        private Optional<String> string = Optional.empty();

        private Builder() {}

        /**
         * Establece la profundidad principal.
         *
         * @param depth profundidad en plies
         * @return este builder
         */
        public Builder depth(int depth) {
            this.depth = Optional.of(depth);
            return this;
        }

        /**
         * Establece la profundidad selectiva.
         *
         * @param seldepth profundidad selectiva en plies
         * @return este builder
         */
        public Builder seldepth(int seldepth) {
            this.seldepth = Optional.of(seldepth);
            return this;
        }

        /**
         * Establece el índice MultiPV.
         *
         * @param multipv índice MultiPV
         * @return este builder
         */
        public Builder multipv(int multipv) {
            this.multipv = Optional.of(multipv);
            return this;
        }

        /**
         * Establece la evaluación.
         *
         * @param score evaluación no nula
         * @return este builder
         * @throws NullPointerException si {@code score} es {@code null}
         */
        public Builder score(Score score) {
            this.score = Optional.of(Objects.requireNonNull(score, "score must not be null"));
            return this;
        }

        /**
         * Establece el total de nodos visitados.
         *
         * @param nodes cantidad de nodos
         * @return este builder
         */
        public Builder nodes(long nodes) {
            this.nodes = Optional.of(nodes);
            return this;
        }

        /**
         * Establece los nodos por segundo.
         *
         * @param nps nodos por segundo
         * @return este builder
         */
        public Builder nps(long nps) {
            this.nps = Optional.of(nps);
            return this;
        }

        /**
         * Establece el nivel de uso de la tabla hash (por mil).
         *
         * @param hashfull uso de hash
         * @return este builder
         */
        public Builder hashfull(int hashfull) {
            this.hashfull = Optional.of(hashfull);
            return this;
        }

        /**
         * Establece los accesos a tablas de finales.
         *
         * @param tbhits accesos a tablebases
         * @return este builder
         */
        public Builder tbhits(long tbhits) {
            this.tbhits = Optional.of(tbhits);
            return this;
        }

        /**
         * Establece el tiempo de búsqueda transcurrido.
         *
         * @param time duración no nula
         * @return este builder
         * @throws NullPointerException si {@code time} es {@code null}
         */
        public Builder time(Duration time) {
            this.time = Optional.of(Objects.requireNonNull(time, "time must not be null"));
            return this;
        }

        /**
         * Establece la variante principal ({@code pv}).
         *
         * @param pv lista de jugadas UCI
         * @return este builder
         * @throws NullPointerException si {@code pv} o algún elemento es {@code null}
         */
        public Builder pv(List<String> pv) {
            this.pv = List.copyOf(Objects.requireNonNull(pv, "pv must not be null"));
            return this;
        }

        /**
         * Establece la jugada raíz evaluada actualmente.
         *
         * @param currmove jugada en notación UCI
         * @return este builder
         * @throws NullPointerException si {@code currmove} es {@code null}
         */
        public Builder currmove(String currmove) {
            this.currmove = Optional.of(Objects.requireNonNull(currmove, "currmove must not be null"));
            return this;
        }

        /**
         * Establece el índice de la jugada evaluada actualmente.
         *
         * @param currmovenumber número de jugada
         * @return este builder
         */
        public Builder currmovenumber(int currmovenumber) {
            this.currmovenumber = Optional.of(currmovenumber);
            return this;
        }

        /**
         * Establece el texto libre informativo.
         *
         * @param string cadena de texto
         * @return este builder
         * @throws NullPointerException si {@code string} es {@code null}
         */
        public Builder string(String string) {
            this.string = Optional.of(Objects.requireNonNull(string, "string must not be null"));
            return this;
        }

        /**
         * Construye la instancia inmutable de {@link Info}.
         *
         * @return nueva instancia de {@link Info}
         */
        public Info build() {
            return new Info(
                    depth,
                    seldepth,
                    multipv,
                    score,
                    nodes,
                    nps,
                    hashfull,
                    tbhits,
                    time,
                    pv,
                    currmove,
                    currmovenumber,
                    string);
        }
    }
}
