package io.github.jesores.uci.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModelValidationTest {

    @Test
    void positionCopiesMovesImmutablyAndRejectsInvalidTokens() {
        List<String> mutable = new ArrayList<>(List.of("e2e4", "e7e8q"));
        Position pos = Position.startpos(mutable);
        mutable.add("g1f3");

        assertEquals(Optional.empty(), pos.fen());
        assertEquals(List.of("e2e4", "e7e8q"), pos.moves());
        assertThrows(UnsupportedOperationException.class, () -> pos.moves().add("a2a3"));

        assertThrows(IllegalArgumentException.class, () -> Position.fen("  "));
        assertThrows(IllegalArgumentException.class, () -> Position.fen("8/8/8/8/8/8/8/8 w - - 0 1\nquit"));
        assertThrows(IllegalArgumentException.class, () -> Position.startpos("e2 e4"));
        assertThrows(NullPointerException.class, () -> Position.fen(null));
    }

    @Test
    void scoreAndEngineOptionSupportExhaustivePatternMatching() {
        Score cp = Score.cp(42, Score.Bound.LOWERBOUND);
        Score mate = Score.mate(-3);

        String cpSummary = describeScore(cp);
        String mateSummary = describeScore(mate);
        assertEquals("cp:42:LOWERBOUND", cpSummary);
        assertEquals("mate:-3:EXACT", mateSummary);

        EngineOption spin = new EngineOption.Spin("Hash", 16, 1, 1024);
        assertEquals("spin:Hash:16", describeOption(spin));
        assertThrows(IllegalArgumentException.class, () -> new EngineOption.Spin("Bad", 5, 10, 1));
    }

    @Test
    void limitAndPlayResultEnforceInvariants() {
        Limit limit = Limit.builder()
                .depth(12)
                .nodes(50_000L)
                .moveTime(Duration.ofMillis(250))
                .searchMoves(List.of("e2e4", "d2d4"))
                .build();

        assertEquals(Optional.of(12), limit.depth());
        assertEquals(List.of("e2e4", "d2d4"), limit.searchMoves());
        assertThrows(IllegalArgumentException.class, () -> Limit.depth(-1));
        assertThrows(IllegalArgumentException.class, () -> Limit.builder().movesToGo(0).build());
        assertThrows(IllegalArgumentException.class, () -> Limit.moveTime(Duration.ofMillis(-5)));

        Info info1 = Info.builder().depth(1).score(Score.cp(10)).build();
        Info info2 = Info.builder().depth(2).score(Score.cp(25)).pv(List.of("e2e4", "e7e5")).build();
        PlayResult playResult = new PlayResult(Optional.of("e2e4"), Optional.of("e7e5"), List.of(info1, info2));

        assertEquals(Optional.of(info2), playResult.lastInfo());
        assertTrue(new PlayResult(Optional.empty(), Optional.empty(), List.of()).lastInfo().isEmpty());
    }

    private static String describeScore(Score score) {
        return switch (score) {
            case Score.Cp(var value, var bound) -> "cp:" + value + ":" + bound;
            case Score.Mate(var moves, var bound) -> "mate:" + moves + ":" + bound;
        };
    }

    private static String describeOption(EngineOption option) {
        return switch (option) {
            case EngineOption.Check(var name, var def) -> "check:" + name + ":" + def;
            case EngineOption.Spin(var name, var def, var min, var max) -> "spin:" + name + ":" + def;
            case EngineOption.Combo(var name, var def, var choices) -> "combo:" + name + ":" + choices.size();
            case EngineOption.Button(var name) -> "button:" + name;
            case EngineOption.StringOption(var name, var def) -> "string:" + name + ":" + def.orElse("");
        };
    }
}
