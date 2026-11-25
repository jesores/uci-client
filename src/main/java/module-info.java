/**
 * Biblioteca minimalista para comunicarse de forma síncrona y asíncrona con motores de ajedrez UCI.
 *
 * <p>Únicamente se exportan los paquetes de la API pública ({@code io.github.jesores.uci.api})
 * y de modelos inmutables ({@code io.github.jesores.uci.model}).
 */
module io.github.jesores.uci {
    exports io.github.jesores.uci.api;
    exports io.github.jesores.uci.model;
}
