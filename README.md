# uci-client

Biblioteca minimalista en **Java 21+ (Maven + JPMS)** para comunicarse de forma **síncrona y asíncrona** con motores de ajedrez compatibles con el protocolo **UCI** (*Universal Chess Interface*), inspirada en `chess.engine` de `python-chess`.

## Características principales

- **Cero dependencias en tiempo de ejecución:** únicamente requiere `java.base`.
- **Doble API clara:**
  - `UciClient`: fachada síncrona bloqueante con soporte de *timeouts* y `try-with-resources`.
  - `AsyncUciClient`: interfaz asíncrona no bloqueante basada en `CompletableFuture`.
- **Modelos inmutables de Java 21:** `record`, `sealed interface` (`Score`, `EngineOption`) listos para *pattern matching* exhaustivo.
- **Concurrencia segura:** despacho de eventos `Info` en un hilo dedicado (`uci-callback`), drenado automático de `stderr` y liberación garantizada del subproceso al cerrar o ante fallos.
- **Alcance acotado:** trabaja exclusivamente con cadenas **FEN** y jugadas en notación coordenada UCI (`e2e4`, `e7e8q`, `e1g1`). No incluye representación de tablero, validación de reglas de ajedrez ni lectura de PGN.

---

## Requisitos

- **Java 21** o superior.
- **Maven 3.9+** (para compilar o instalar localmente).
- Un binario ejecutable de cualquier motor UCI instalado en el sistema (por ejemplo, `stockfish`).

---

## Instalación

### 1. Instalar en el repositorio Maven local

```bash
mvn clean install
```

### 2. Agregar la dependencia en `pom.xml`

```xml
<dependency>
  <groupId>io.github.jesores</groupId>
  <artifactId>uci-client</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 3. Declarar el módulo JPMS (si tu proyecto usa `module-info.java`)

```java
module com.ejemplo.miapp {
    requires io.github.jesores.uci;
}
```

Paquetes públicos exportados por el módulo:
- `io.github.jesores.uci.api`
- `io.github.jesores.uci.model`

---

## Inicio rápido (API síncrona)

Usa `UciClient` dentro de un bloque `try-with-resources` para iniciar el motor, completar el *handshake* UCI (`uciok` + `readyok`), analizar una posición y cerrar el subproceso limpiamente:

```java
import io.github.jesores.uci.api.UciClient;
import io.github.jesores.uci.model.Info;
import io.github.jesores.uci.model.Limit;
import io.github.jesores.uci.model.PlayResult;
import io.github.jesores.uci.model.Position;
import io.github.jesores.uci.model.Score;
import java.nio.file.Path;

public class EjemploSincrono {
    public static void main(String[] args) {
        Path motor = Path.of("/usr/bin/stockfish");

        try (UciClient client = UciClient.open(motor)) {
            System.out.println("Motor: " + client.engineId().name().orElse("desconocido"));

            client.setOption("Hash", "64");
            client.setOption("Threads", "2");
            client.newGame();

            Position posicion = Position.startpos("e2e4", "e7e5", "g1f3");
            PlayResult resultado = client.play(posicion, Limit.depth(15));

            System.out.println("Mejor jugada: " + resultado.bestMove().orElse("(ninguna)"));
            System.out.println("Ponder: " + resultado.ponderMove().orElse("-"));

            resultado.lastInfo().flatMap(Info::score).ifPresent(score -> {
                String texto = switch (score) {
                    case Score.Cp(var valor, var cota) -> valor + " cp (" + cota + ")";
                    case Score.Mate(var jugadas, var cota) -> "Mate en " + jugadas + " (" + cota + ")";
                };
                System.out.println("Evaluación: " + texto);
            });
        }
    }
}
```

> **Nota sobre posiciones terminales:** si la posición está en jaque mate o ahogado y el motor responde `bestmove (none)` o `bestmove 0000`, `resultado.bestMove()` devolverá `Optional.empty()`.

---

## Uso asíncrono (`AsyncUciClient`)

`AsyncUciClient` expone todas las operaciones mediante `CompletableFuture` sin bloquear hilos de la aplicación:

```java
import io.github.jesores.uci.api.AsyncUciClient;
import io.github.jesores.uci.model.Limit;
import io.github.jesores.uci.model.Position;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class EjemploAsincrono {
    public static void main(String[] args) {
        Path motor = Path.of("/usr/bin/stockfish");

        CompletableFuture<Void> flujo = AsyncUciClient.open(motor).thenCompose(client ->
            client.setOption("Hash", "128")
                .thenCompose(v -> client.newGame())
                .thenCompose(v -> client.play(
                    Position.startpos("d2d4", "g8f6"),
                    Limit.moveTime(Duration.ofMillis(500)),
                    info -> info.depth().ifPresent(d ->
                        System.out.println("Profundidad " + d + " -> PV: " + info.pv()))
                ))
                .thenAccept(res ->
                    System.out.println("Jugada elegida: " + res.bestMove().orElse("(ninguna)")))
                .whenComplete((ok, err) -> client.closeAsync())
        );

        flujo.join();
    }
}
```

---

## Cheat Sheet de la API

### 1. Operaciones del cliente (`UciClient` vs `AsyncUciClient`)

| Acción | Síncrono (`UciClient` — bloquea el hilo actual) | Asíncrono (`AsyncUciClient` — no bloquea, ideal para GUIs) | Protocolo UCI subyacente |
| :--- | :--- | :--- | :--- |
| **Abrir motor** | `UciClient.open(path)` / `UciClient.open(config)` | `AsyncUciClient.open(path)` / `AsyncUciClient.open(config)` → `CompletableFuture<AsyncUciClient>` | `uci` → `uciok` + `isready` → `readyok` |
| **Identidad del motor** | `client.engineId()` → `EngineId` | `asyncClient.engineId()` → `EngineId` | Capturado en `id name` / `id author` |
| **Opciones disponibles** | `client.options()` → `Map<String, EngineOption>` | `asyncClient.options()` → `Map<String, EngineOption>` | Capturado en `option name ...` |
| **Estado de sesión** | `client.isOpen()` → `boolean` | `asyncClient.isOpen()` → `boolean` | Estado interno activo |
| **Verificar disponibilidad** | `client.ping()` / `client.ping(timeout)` | `asyncClient.ping()` → `CompletableFuture<Void>` | `isready` → `readyok` |
| **Nueva partida** | `client.newGame()` / `client.newGame(timeout)` | `asyncClient.newGame()` → `CompletableFuture<Void>` | `ucinewgame` + `isready` → `readyok` |
| **Asignar opción** | `client.setOption("Hash", "64")` | `asyncClient.setOption("Hash", "64")` → `CompletableFuture<Void>` | `setoption name ... value ...` + `isready` |
| **Accionar botón** | `client.triggerButton("Clear Hash")` | `asyncClient.triggerButton("Clear Hash")` → `CompletableFuture<Void>` | `setoption name ...` + `isready` |
| **Analizar / Jugar** | `client.play(pos, limit)` → `PlayResult` | `asyncClient.play(pos, limit)` → `CompletableFuture<PlayResult>` | `position ...` + `go ...` → `bestmove` |
| **Analizar con `Info` en vivo** | `client.play(pos, limit, onInfo)` / `client.play(pos, limit, onInfo, timeout)` | `asyncClient.play(pos, limit, onInfo)` → `CompletableFuture<PlayResult>` | Flujo `info ...` en hilo `uci-callback` |
| **Detener búsqueda** | `client.stop()` | `asyncClient.stop()` → `CompletableFuture<Void>` | `stop` → espera `bestmove` |
| **Cerrar motor** | `client.close()` *(compatible con `try-with-resources`)* | `asyncClient.closeAsync()` → `CompletableFuture<Void>` / `close()` | `quit` + liberación de proceso y tuberías |

### 2. Construcción rápida de modelos y lectura de resultados

| Modelo | Cómo crearlo / consultarlo |
| :--- | :--- |
| **`Position`** | `Position.startpos()` · `Position.startpos("e2e4", "e7e5")` · `Position.fen(fen)` · `Position.fen(fen, "e7e8q")` |
| **`Limit`** | `Limit.depth(18)` · `Limit.moveTime(Duration.ofMillis(500))` · `Limit.nodes(200_000L)` · `Limit.mate(3)` · `Limit.clock(wtime, btime, winc, binc)` · `Limit.infinite()` · `Limit.builder()...build()` |
| **`UciConfig`** | `UciConfig.of(path)` · `UciConfig.builder(path).arguments(...).workingDirectory(...).handshakeTimeout(...).defaultTimeout(...).closeTimeout(...).build()` |
| **`PlayResult`** | `res.bestMove()` (`Optional<String>`) · `res.ponderMove()` (`Optional<String>`) · `res.info()` (`List<Info>`) · `res.lastInfo()` (`Optional<Info>`) |
| **`Info`** | `info.depth()`, `info.seldepth()`, `info.multipv()`, `info.score()`, `info.nodes()`, `info.nps()`, `info.hashfull()`, `info.tbhits()`, `info.time()`, `info.pv()`, `info.currmove()`, `info.currmovenumber()`, `info.string()` |
| **`Score`** | `Score.Cp(int value, Score.Bound bound)` · `Score.Mate(int moves, Score.Bound bound)` |

### 3. Regla rápida para interfaces gráficas (JavaFX / Swing)

- **Nunca llames `UciClient` en el hilo gráfico** (bloqueará la ventana mientras piensa el motor); usa **`AsyncUciClient`**.
- Los callbacks `Consumer<Info>` y continuaciones `.thenAccept(...)` se ejecutan en el hilo interno `uci-callback`: envuelve los cambios visuales con `Platform.runLater(...)` (JavaFX) o `SwingUtilities.invokeLater(...)` (Swing).

---

## Guías de uso común

### 1. Posiciones con FEN y coronaciones UCI

`Position` admite tanto `startpos` como cualquier cadena FEN válida, seguida opcionalmente de jugadas en notación coordenada UCI:

```java
// Desde la posición inicial
Position apertura = Position.startpos("e2e4", "c7c5", "g1f3");

// Desde un FEN arbitrario + coronación (e7e8q)
Position finalPeon = Position.fen(
    "8/4P1k1/8/8/8/8/6K1/8 w - - 0 1",
    "e7e8q"
);
```

### 2. Límites de búsqueda y controles de reloj (`Limit`)

```java
// Por profundidad, nodos, mate o tiempo fijo por jugada
Limit porProfundidad = Limit.depth(20);
Limit porNodos       = Limit.nodes(500_000L);
Limit buscarMate     = Limit.mate(4);
Limit medioSegundo   = Limit.moveTime(Duration.ofMillis(500));

// Con relojes e incrementos (3 min + 2 s de incremento)
Limit partidaConReloj = Limit.clock(
    Duration.ofMinutes(3), // tiempo blancas (wtime)
    Duration.ofMinutes(3), // tiempo negras (btime)
    Duration.ofSeconds(2), // incremento blancas (winc)
    Duration.ofSeconds(2)  // incremento negras (binc)
);

// Combinación personalizada con Builder (p. ej. restringir jugadas candidatas)
Limit personalizado = Limit.builder()
    .depth(18)
    .searchMoves(java.util.List.of("e2e4", "d2d4"))
    .movesToGo(25)
    .build();
```

### 3. Análisis infinito y detención con `stop()`

Los callbacks `Consumer<Info>` se ejecutan en orden en un hilo interno dedicado (`uci-callback`) y se garantiza que todos han terminado antes de que `play(...)` devuelva el `PlayResult`:

```java
CompletableFuture<PlayResult> busqueda = asyncClient.play(
    Position.startpos(),
    Limit.infinite(),
    info -> {
        if (info.depth().orElse(0) >= 16) {
            asyncClient.stop(); // Idempotente y seguro desde cualquier hilo
        }
    }
);

PlayResult resultado = busqueda.join();
```

### 4. Inspección de opciones anunciadas por el motor (`EngineOption`)

Tras abrir el cliente, `options()` devuelve un mapa inmutable con todas las opciones reportadas por el motor durante el *handshake*:

```java
import io.github.jesores.uci.model.EngineOption;

client.options().values().forEach(opcion -> {
    switch (opcion) {
        case EngineOption.Check(var name, var def) ->
            System.out.printf("[check]  %s (default=%s)%n", name, def);
        case EngineOption.Spin(var name, var def, var min, var max) ->
            System.out.printf("[spin]   %s (default=%d, rango=%d..%d)%n", name, def, min, max);
        case EngineOption.Combo(var name, var def, var choices) ->
            System.out.printf("[combo]  %s (default=%s, opciones=%s)%n", name, def.orElse("-"), choices);
        case EngineOption.Button(var name) ->
            System.out.printf("[button] %s%n", name);
        case EngineOption.StringOption(var name, var def) ->
            System.out.printf("[string] %s (default='%s')%n", name, def.orElse(""));
    }
});

// Accionar una opción tipo button (p. ej. limpiar tabla hash)
client.triggerButton("Clear Hash");

// Limpiar una opción de tipo string (se envía automáticamente como <empty> según UCI)
client.setOption("SyzygyPath", "");
```

### 5. Configuración avanzada del proceso y *timeouts* (`UciConfig`)

```java
import io.github.jesores.uci.api.UciConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

UciConfig config = UciConfig.builder(Path.of("/opt/engines/lc0"))
    .arguments(List.of("--weights=/opt/engines/network.pb.gz"))
    .workingDirectory(Path.of("/opt/engines"))
    .handshakeTimeout(Duration.ofSeconds(15))
    .defaultTimeout(Duration.ofMinutes(1))
    .closeTimeout(Duration.ofSeconds(3))
    .build();

try (UciClient client = UciClient.open(config)) {
    client.ping(Duration.ofSeconds(2));
}
```

---

## Cierre recomendado de conexiones y ciclo de vida

Cada cliente abierto mantiene un subproceso nativo del motor UCI y tres hilos `daemon` internos (`uci-reader`, `uci-stderr`, `uci-callback`). Sigue estas prácticas según el tipo de aplicación:

### 1. Aplicaciones CLI, servicios y *virtual threads* (`UciClient`)
Usa siempre **`try-with-resources`** para garantizar el envío de `quit` y la liberación inmediata del subproceso incluso si ocurre una excepción:

```java
try (UciClient client = UciClient.open(Path.of("/usr/bin/stockfish"))) {
    PlayResult res = client.play(Position.startpos(), Limit.depth(12));
} // envía quit -> cierra tuberías -> destroy/destroyForcibly si no responde
```

### 2. Aplicaciones JavaFX (`AsyncUciClient`)
Cierra explícitamente el cliente en el método `Application.stop()` (invocado por JavaFX al cerrar la ventana o llamar a `Platform.exit()`), y cierra siempre el motor anterior antes de abrir otro si el usuario cambia de motor en tiempo de ejecución:

```java
public class MiAppAjedrez extends Application {
    private AsyncUciClient engine;

    @Override
    public void stop() {
        if (engine != null) {
            engine.close();
        }
    }
}
```

### 3. Aplicaciones Swing (`AsyncUciClient`)
Registra el cierre en el `WindowListener` de la ventana principal:

```java
frame.addWindowListener(new WindowAdapter() {
    @Override
    public void windowClosed(WindowEvent e) {
        if (engine != null) {
            engine.closeAsync();
        }
    }
});
```

### 4. Flujos asíncronos encadenados (`CompletableFuture`)
Asegura la liberación en `whenComplete` al terminar la cadena asíncrona:

```java
AsyncUciClient.open(motor).thenCompose(client ->
    client.play(Position.startpos(), Limit.depth(10))
          .whenComplete((res, err) -> client.closeAsync())
);
```

### Red de seguridad automática (`ShutdownHook`)
Además del cierre explícito, la biblioteca registra automáticamente un **`ShutdownHook` en la JVM** (`uci-shutdown-hook`) por cada proceso lanzado:
- Si la aplicación termina (`Platform.exit()`, `System.exit()`, `Ctrl+C` o `SIGTERM`) y se olvidó invocar `close()`, el hook envía `quit`, espera brevemente y ejecuta `destroy()` / `destroyForcibly()` para evitar procesos huérfanos consumiendo CPU.
- Cuando sí llamas `close()` / `closeAsync()`, el hook se **desregistra automáticamente** para no retener memoria durante sesiones largas.
- *(Nota del sistema operativo: una terminación abrupta por `SIGKILL` / `kill -9` mata a la JVM desde el kernel sin ejecutar código Java).*

---

## Manejo de errores y garantías de concurrencia

Todas las excepciones de la biblioteca extienden de `UciException` (`RuntimeException`):

| Excepción | Cuándo ocurre |
| :--- | :--- |
| `UciTimeoutException` | El *handshake* o una operación supera el tiempo máximo configurado. Si ocurre durante `play(...)`, el cliente envía `stop` automáticamente al motor. |
| `UciProtocolException` | Uso inválido del estado UCI (por ejemplo, lanzar dos búsquedas `play(...)` simultáneas o invocar `setOption(...)` / `newGame()` mientras hay una búsqueda activa). |
| `UciEngineTerminatedException` | El proceso del motor termina de forma inesperada (`EOF`), falla la tubería de E/S o se invoca una operación sobre un cliente cerrado. |
| `UciException` | Fallo general al lanzar el proceso ejecutable o interrupción de hilo durante una espera síncrona. |

### Reglas de concurrencia
- **Una búsqueda a la vez:** el protocolo UCI no permite dos comandos `go` concurrentes sobre el mismo proceso.
- **Operaciones permitidas durante una búsqueda:** `stop()`, `ping()` (`isready`/`readyok`) y `close()` / `closeAsync()` son seguros de invocar desde cualquier hilo mientras `play(...)` está en ejecución.

---

## Diagnóstico y trazas del protocolo UCI

La biblioteca utiliza exclusivamente `System.Logger` del JDK (`io.github.jesores.uci.*`):
- Nivel `TRACE`: registra cada línea enviada (`>>`) y recibida (`<<`) por `stdin`/`stdout`.
- Nivel `DEBUG`: registra el arranque/cierre del subproceso y la salida `stderr` del motor.

Para habilitar las trazas con el backend por defecto del JDK (`java.util.logging`), crea un archivo `logging.properties`:

```properties
handlers = java.util.logging.ConsoleHandler
java.util.logging.ConsoleHandler.level = ALL
io.github.jesores.uci.level = ALL
```

Y ejecuta tu aplicación con:

```bash
java -Djava.util.logging.config.file=logging.properties -jar mi-app.jar
```

---

## Desarrollo y pruebas

```bash
mvn test
mvn clean package
```
