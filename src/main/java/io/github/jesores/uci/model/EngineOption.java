package io.github.jesores.uci.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Opción configurable anunciada por el motor UCI mediante comandos {@code option name ... type ...}.
 *
 * <p>Jerarquía cerrada e inmutable, segura para uso concurrente (<i>thread-safe</i>).
 */
public sealed interface EngineOption
        permits EngineOption.Check,
                EngineOption.Spin,
                EngineOption.Combo,
                EngineOption.Button,
                EngineOption.StringOption {

    /**
     * Devuelve el nombre identificador de la opción UCI.
     *
     * @return nombre no vacío de la opción
     */
    String name();

    /**
     * Opción booleana ({@code type check}).
     *
     * @param name         nombre de la opción
     * @param defaultValue valor booleano por defecto
     */
    record Check(String name, boolean defaultValue) implements EngineOption {

        /**
         * Construye una opción booleana validando su nombre.
         *
         * @param name         nombre de la opción
         * @param defaultValue valor por defecto
         * @throws NullPointerException     si {@code name} es {@code null}
         * @throws IllegalArgumentException si {@code name} está en blanco
         */
        public Check {
            validateName(name);
        }
    }

    /**
     * Opción entera acotada ({@code type spin}).
     *
     * @param name         nombre de la opción
     * @param defaultValue valor numérico por defecto
     * @param min          valor mínimo permitido
     * @param max          valor máximo permitido
     */
    record Spin(String name, long defaultValue, long min, long max) implements EngineOption {

        /**
         * Construye una opción numérica validando su nombre y rango.
         *
         * @param name         nombre de la opción
         * @param defaultValue valor por defecto
         * @param min          valor mínimo
         * @param max          valor máximo
         * @throws NullPointerException     si {@code name} es {@code null}
         * @throws IllegalArgumentException si {@code name} está en blanco o {@code min > max}
         */
        public Spin {
            validateName(name);
            if (min > max) {
                throw new IllegalArgumentException("min (" + min + ") must be <= max (" + max + ")");
            }
        }
    }

    /**
     * Opción de selección entre valores predefinidos ({@code type combo}).
     *
     * @param name         nombre de la opción
     * @param defaultValue valor seleccionado por defecto, si el motor lo especificó
     * @param choices      lista inmutable de valores permitidos ({@code var})
     */
    record Combo(String name, Optional<String> defaultValue, List<String> choices) implements EngineOption {

        /**
         * Construye una opción de selección realizando copia defensiva de las alternativas.
         *
         * @param name         nombre de la opción
         * @param defaultValue valor por defecto opcional
         * @param choices      alternativas declaradas
         * @throws NullPointerException     si algún argumento o elemento de {@code choices} es {@code null}
         * @throws IllegalArgumentException si {@code name} está en blanco
         */
        public Combo {
            validateName(name);
            Objects.requireNonNull(defaultValue, "defaultValue must not be null");
            Objects.requireNonNull(choices, "choices must not be null");
            choices = List.copyOf(choices);
        }
    }

    /**
     * Acción inmediata sin valor asociado ({@code type button}).
     *
     * @param name nombre de la opción
     */
    record Button(String name) implements EngineOption {

        /**
         * Construye una opción de tipo botón validando su nombre.
         *
         * @param name nombre de la opción
         * @throws NullPointerException     si {@code name} es {@code null}
         * @throws IllegalArgumentException si {@code name} está en blanco
         */
        public Button {
            validateName(name);
        }
    }

    /**
     * Opción de texto libre ({@code type string}).
     *
     * @param name         nombre de la opción
     * @param defaultValue valor de texto por defecto, si el motor lo especificó
     */
    record StringOption(String name, Optional<String> defaultValue) implements EngineOption {

        /**
         * Construye una opción de texto validando sus argumentos.
         *
         * @param name         nombre de la opción
         * @param defaultValue valor por defecto opcional
         * @throws NullPointerException     si {@code name} o {@code defaultValue} son {@code null}
         * @throws IllegalArgumentException si {@code name} está en blanco
         */
        public StringOption {
            validateName(name);
            Objects.requireNonNull(defaultValue, "defaultValue must not be null");
        }
    }

    private static void validateName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("option name must not be blank");
        }
    }
}
