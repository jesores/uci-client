# uci-client

[![JitPack](https://jitpack.io/v/jesores/uci-client.svg)](https://jitpack.io/#jesores/uci-client)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)](https://openjdk.org/projects/jdk/21/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-green.svg)](LICENSE)

> 🌐 **Language:** English | [Español](README_es.md)

Minimalist **Java 21+ (Maven + JPMS)** client library for **synchronous and asynchronous** communication with **UCI** (*Universal Chess Interface*) chess engines, inspired by `python-chess`'s `chess.engine`.

## Key Features

- **Zero runtime dependencies:** requires only `java.base`.
- **Clean dual API:**
  - `UciClient`: blocking synchronous facade with configurable timeouts and `try-with-resources` support.
  - `AsyncUciClient`: non-blocking asynchronous interface powered by `CompletableFuture`.
- **Immutable Java 21 domain models:** `record` types and `sealed` interfaces (`Score`, `EngineOption`) designed for exhaustive `switch` pattern matching.
- **Safe concurrency & process management:** ordered `Info` event dispatch on a dedicated thread (`uci-callback`), automatic `stderr` draining, and guaranteed subprocess cleanup on close or failure.
- **Focused scope:** works strictly with **FEN** strings and UCI coordinate moves (`e2e4`, `e7e8q`, `e1g1`). Does not bundle board representation, chess move validation, or PGN parsing.

---

## Requirements

- **Java 21** or newer.
- **Maven 3.9+** (if building or installing locally from source).
- Any UCI-compatible chess engine binary installed on your system (e.g., `stockfish`).

---

## Installation

### Option A: Via JitPack (recommended)

#### Maven (`pom.xml`)

Add the JitPack repository and the dependency pointing to the release tag (`v1.0.0`):

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.github.jesores</groupId>
    <artifactId>uci-client</artifactId>
    <version>v1.0.0</version>
  </dependency>
</dependencies>
```

#### Gradle (`build.gradle.kts`)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.jesores:uci-client:v1.0.0")
}
```

### Option B: Build and install locally

```bash
mvn clean install
```

```xml
<dependency>
  <groupId>io.github.jesores</groupId>
  <artifactId>uci-client</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Declare the JPMS module (if your project uses `module-info.java`)

```java
module com.example.myapp {
    requires io.github.jesores.uci;
}
```

Public packages exported by the module:
- `io.github.jesores.uci.api`
- `io.github.jesores.uci.model`

---

## Quick Start (Synchronous API)

Use `UciClient` inside a `try-with-resources` block to launch the engine, complete the UCI handshake (`uciok` + `readyok`), analyze a position, and cleanly shut down the subprocess:

```java
import io.github.jesores.uci.api.UciClient;
import io.github.jesores.uci.model.Info;
import io.github.jesores.uci.model.Limit;
import io.github.jesores.uci.model.PlayResult;
import io.github.jesores.uci.model.Position;
import io.github.jesores.uci.model.Score;
import java.nio.file.Path;

public class SyncExample {
    public static void main(String[] args) {
        Path enginePath = Path.of("/usr/bin/stockfish");

        try (UciClient client = UciClient.open(enginePath)) {
            System.out.println("Engine: " + client.engineId().name().orElse("unknown"));

            client.setOption("Hash", "64");
            client.setOption("Threads", "2");
            client.newGame();

            Position position = Position.startpos("e2e4", "e7e5", "g1f3");
            PlayResult result = client.play(position, Limit.depth(15));

            System.out.println("Best move: " + result.bestMove().orElse("(none)"));
            System.out.println("Ponder: " + result.ponderMove().orElse("-"));

            result.lastInfo().flatMap(Info::score).ifPresent(score -> {
                String eval = switch (score) {
                    case Score.Cp(var value, var bound) -> value + " cp (" + bound + ")";
                    case Score.Mate(var moves, var bound) -> "Mate in " + moves + " (" + bound + ")";
                };
                System.out.println("Evaluation: " + eval);
            });
        }
    }
}
```

> **Note on terminal positions:** if the position is checkmate or stalemate and the engine replies `bestmove (none)` or `bestmove 0000`, `result.bestMove()` returns `Optional.empty()`.

---

## Asynchronous Usage (`AsyncUciClient`)

`AsyncUciClient` exposes every operation via `CompletableFuture` without blocking application threads:

```java
import io.github.jesores.uci.api.AsyncUciClient;
import io.github.jesores.uci.model.Limit;
import io.github.jesores.uci.model.Position;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AsyncExample {
    public static void main(String[] args) {
        Path enginePath = Path.of("/usr/bin/stockfish");

        CompletableFuture<Void> pipeline = AsyncUciClient.open(enginePath).thenCompose(client ->
            client.setOption("Hash", "128")
                .thenCompose(v -> client.newGame())
                .thenCompose(v -> client.play(
                    Position.startpos("d2d4", "g8f6"),
                    Limit.moveTime(Duration.ofMillis(500)),
                    info -> info.depth().ifPresent(d ->
                        System.out.println("Depth " + d + " -> PV: " + info.pv()))
                ))
                .thenAccept(res ->
                    System.out.println("Selected move: " + res.bestMove().orElse("(none)")))
                .whenComplete((ok, err) -> client.closeAsync())
        );

        pipeline.join();
    }
}
```

---

## API Cheat Sheet

### 1. Client Operations (`UciClient` vs `AsyncUciClient`)

| Action | Synchronous (`UciClient` — blocks calling thread) | Asynchronous (`AsyncUciClient` — non-blocking, ideal for GUIs) | Underlying UCI Protocol |
| :--- | :--- | :--- | :--- |
| **Open engine** | `UciClient.open(path)` / `UciClient.open(config)` | `AsyncUciClient.open(path)` / `AsyncUciClient.open(config)` → `CompletableFuture<AsyncUciClient>` | `uci` → `uciok` + `isready` → `readyok` |
| **Engine identity** | `client.engineId()` → `EngineId` | `asyncClient.engineId()` → `EngineId` | Captured from `id name` / `id author` |
| **Available options** | `client.options()` → `Map<String, EngineOption>` | `asyncClient.options()` → `Map<String, EngineOption>` | Captured from `option name ...` |
| **Session state** | `client.isOpen()` → `boolean` | `asyncClient.isOpen()` → `boolean` | Internal active state |
| **Check readiness** | `client.ping()` / `client.ping(timeout)` | `asyncClient.ping()` → `CompletableFuture<Void>` | `isready` → `readyok` |
| **New game** | `client.newGame()` / `client.newGame(timeout)` | `asyncClient.newGame()` → `CompletableFuture<Void>` | `ucinewgame` + `isready` → `readyok` |
| **Set option** | `client.setOption("Hash", "64")` | `asyncClient.setOption("Hash", "64")` → `CompletableFuture<Void>` | `setoption name ... value ...` + `isready` |
| **Trigger button** | `client.triggerButton("Clear Hash")` | `asyncClient.triggerButton("Clear Hash")` → `CompletableFuture<Void>` | `setoption name ...` + `isready` |
| **Analyze / Play** | `client.play(pos, limit)` → `PlayResult` | `asyncClient.play(pos, limit)` → `CompletableFuture<PlayResult>` | `position ...` + `go ...` → `bestmove` |
| **Analyze with live `Info`** | `client.play(pos, limit, onInfo)` / `client.play(pos, limit, onInfo, timeout)` | `asyncClient.play(pos, limit, onInfo)` → `CompletableFuture<PlayResult>` | `info ...` stream on `uci-callback` thread |
| **Stop search** | `client.stop()` | `asyncClient.stop()` → `CompletableFuture<Void>` | `stop` → awaits `bestmove` |
| **Close engine** | `client.close()` *(`try-with-resources` compatible)* | `asyncClient.closeAsync()` → `CompletableFuture<Void>` / `close()` | `quit` + process & pipe teardown |

### 2. Model Builders & Result Accessors

| Model | How to create / inspect |
| :--- | :--- |
| **`Position`** | `Position.startpos()` · `Position.startpos("e2e4", "e7e5")` · `Position.fen(fen)` · `Position.fen(fen, "e7e8q")` |
| **`Limit`** | `Limit.depth(18)` · `Limit.moveTime(Duration.ofMillis(500))` · `Limit.nodes(200_000L)` · `Limit.mate(3)` · `Limit.clock(wtime, btime, winc, binc)` · `Limit.infinite()` · `Limit.builder()...build()` |
| **`UciConfig`** | `UciConfig.of(path)` · `UciConfig.builder(path).arguments(...).workingDirectory(...).handshakeTimeout(...).defaultTimeout(...).closeTimeout(...).build()` |
| **`PlayResult`** | `res.bestMove()` (`Optional<String>`) · `res.ponderMove()` (`Optional<String>`) · `res.info()` (`List<Info>`) · `res.lastInfo()` (`Optional<Info>`) |
| **`Info`** | `info.depth()`, `info.seldepth()`, `info.multipv()`, `info.score()`, `info.nodes()`, `info.nps()`, `info.hashfull()`, `info.tbhits()`, `info.time()`, `info.pv()`, `info.currmove()`, `info.currmovenumber()`, `info.string()` |
| **`Score`** | `Score.Cp(int value, Score.Bound bound)` · `Score.Mate(int moves, Score.Bound bound)` |

### 3. Quick Rule for Desktop GUIs (JavaFX / Swing)

- **Never call `UciClient` on the UI thread** (it will freeze the window while the engine thinks); always use **`AsyncUciClient`**.
- `Consumer<Info>` callbacks and `.thenAccept(...)` continuations run on the internal `uci-callback` thread: wrap UI updates in `Platform.runLater(...)` (JavaFX) or `SwingUtilities.invokeLater(...)` (Swing).

---

## Common Usage Guides

### 1. FEN Positions and UCI Promotions

`Position` accepts both `startpos` and any valid FEN string, optionally followed by moves in UCI coordinate notation:

```java
// From the starting position
Position opening = Position.startpos("e2e4", "c7c5", "g1f3");

// From an arbitrary FEN + pawn promotion (e7e8q)
Position pawnEndgame = Position.fen(
    "8/4P1k1/8/8/8/8/6K1/8 w - - 0 1",
    "e7e8q"
);
```

### 2. Search Limits and Clock Controls (`Limit`)

```java
// By depth, nodes, mate distance, or fixed time per move
Limit byDepth    = Limit.depth(20);
Limit byNodes    = Limit.nodes(500_000L);
Limit findMate   = Limit.mate(4);
Limit halfSecond = Limit.moveTime(Duration.ofMillis(500));

// With game clocks and increments (3 min + 2 s increment)
Limit clockedGame = Limit.clock(
    Duration.ofMinutes(3), // White remaining time (wtime)
    Duration.ofMinutes(3), // Black remaining time (btime)
    Duration.ofSeconds(2), // White increment (winc)
    Duration.ofSeconds(2)  // Black increment (binc)
);

// Custom combination with Builder (e.g., restricting candidate moves)
Limit custom = Limit.builder()
    .depth(18)
    .searchMoves(java.util.List.of("e2e4", "d2d4"))
    .movesToGo(25)
    .build();
```

### 3. Infinite Analysis and Stopping with `stop()`

`Consumer<Info>` callbacks execute sequentially on a dedicated internal thread (`uci-callback`), and all callbacks are guaranteed to complete before `play(...)` resolves its `PlayResult`:

```java
CompletableFuture<PlayResult> search = asyncClient.play(
    Position.startpos(),
    Limit.infinite(),
    info -> {
        if (info.depth().orElse(0) >= 16) {
            asyncClient.stop(); // Idempotent and thread-safe from any thread
        }
    }
);

PlayResult result = search.join();
```

### 4. Inspecting Engine-Reported Options (`EngineOption`)

Once opened, `options()` returns an immutable map containing every option advertised by the engine during the UCI handshake:

```java
import io.github.jesores.uci.model.EngineOption;

client.options().values().forEach(option -> {
    switch (option) {
        case EngineOption.Check(var name, var def) ->
            System.out.printf("[check]  %s (default=%s)%n", name, def);
        case EngineOption.Spin(var name, var def, var min, var max) ->
            System.out.printf("[spin]   %s (default=%d, range=%d..%d)%n", name, def, min, max);
        case EngineOption.Combo(var name, var def, var choices) ->
            System.out.printf("[combo]  %s (default=%s, choices=%s)%n", name, def.orElse("-"), choices);
        case EngineOption.Button(var name) ->
            System.out.printf("[button] %s%n", name);
        case EngineOption.StringOption(var name, var def) ->
            System.out.printf("[string] %s (default='%s')%n", name, def.orElse(""));
    }
});

// Trigger a button-type option (e.g., clear hash table)
client.triggerButton("Clear Hash");

// Clear a string-type option (automatically sent as <empty> per UCI spec)
client.setOption("SyzygyPath", "");
```

### 5. Advanced Process & Timeout Configuration (`UciConfig`)

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

## Recommended Lifecycle & Connection Shutdown

Each open client manages a native UCI engine subprocess and three internal `daemon` threads (`uci-reader`, `uci-stderr`, `uci-callback`). Follow these practices based on your application type:

### 1. CLI Apps, Backend Services, and Virtual Threads (`UciClient`)
Always use **`try-with-resources`** to guarantee sending `quit` and immediately releasing the subprocess even if an exception is thrown:

```java
try (UciClient client = UciClient.open(Path.of("/usr/bin/stockfish"))) {
    PlayResult res = client.play(Position.startpos(), Limit.depth(12));
} // sends quit -> closes pipes -> destroy/destroyForcibly if unresponsive
```

### 2. JavaFX Applications (`AsyncUciClient`)
Explicitly close the client inside `Application.stop()` (invoked by JavaFX when the window closes or `Platform.exit()` is called), and always close any previous engine before opening a new one if the user switches engines at runtime:

```java
public class MyChessApp extends Application {
    private AsyncUciClient engine;

    @Override
    public void stop() {
        if (engine != null) {
            engine.close();
        }
    }
}
```

### 3. Swing Applications (`AsyncUciClient`)
Register shutdown cleanup in the main window's `WindowListener`:

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

### 4. Chained Asynchronous Pipelines (`CompletableFuture`)
Ensure cleanup inside `whenComplete` at the end of the async chain:

```java
AsyncUciClient.open(enginePath).thenCompose(client ->
    client.play(Position.startpos(), Limit.depth(10))
          .whenComplete((res, err) -> client.closeAsync())
);
```

### Automatic Safety Net (`ShutdownHook`)
In addition to explicit closing, the library automatically registers a **JVM `ShutdownHook`** (`uci-shutdown-hook`) for every launched process:
- If the application exits (`Platform.exit()`, `System.exit()`, `Ctrl+C`, or `SIGTERM`) and `close()` was forgotten, the hook sends `quit`, waits briefly, and invokes `destroy()` / `destroyForcibly()` to prevent orphaned engine processes from consuming CPU.
- When you explicitly call `close()` / `closeAsync()`, the hook is **automatically unregistered** so long-running sessions never leak memory.
- *(OS note: an abrupt `SIGKILL` / `kill -9` terminates the JVM directly from the kernel without executing Java shutdown hooks).*

---

## Error Handling & Concurrency Guarantees

All library exceptions extend `UciException` (`RuntimeException`):

| Exception | When it occurs |
| :--- | :--- |
| `UciTimeoutException` | Handshake or an operation exceeds its configured timeout. If triggered during `play(...)`, the client automatically sends `stop` to the engine. |
| `UciProtocolException` | Invalid UCI session state usage (e.g., starting two concurrent `play(...)` searches or calling `setOption(...)` / `newGame()` while a search is active). |
| `UciEngineTerminatedException` | The engine subprocess exits unexpectedly (`EOF`), an I/O pipe fails, or an operation is called on a closed client. |
| `UciException` | General failure launching the engine executable or thread interruption while waiting synchronously. |

### Concurrency Rules
- **One search at a time:** the UCI protocol does not allow two concurrent `go` commands on the same engine process.
- **Operations allowed during an active search:** `stop()`, `ping()` (`isready`/`readyok`), and `close()` / `closeAsync()` are thread-safe to invoke from any thread while `play(...)` is running.

---

## Diagnostics & UCI Protocol Tracing

The library exclusively uses the JDK's `System.Logger` (`io.github.jesores.uci.*`):
- `TRACE` level: logs every raw line sent (`>>`) and received (`<<`) over `stdin`/`stdout`.
- `DEBUG` level: logs subprocess startup/teardown and any engine `stderr` output.

To enable protocol traces using the default JDK backend (`java.util.logging`), create a `logging.properties` file:

```properties
handlers = java.util.logging.ConsoleHandler
java.util.logging.ConsoleHandler.level = ALL
io.github.jesores.uci.level = ALL
```

And launch your application with:

```bash
java -Djava.util.logging.config.file=logging.properties -jar my-app.jar
```

---

## Development & Testing

```bash
mvn test
mvn clean package
```
