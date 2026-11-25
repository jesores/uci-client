package io.github.jesores.uci.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jesores.uci.model.EngineOption;
import io.github.jesores.uci.model.Info;
import io.github.jesores.uci.model.Limit;
import io.github.jesores.uci.model.Position;
import io.github.jesores.uci.model.Score;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UciFormatterAndParserTest {

    @Test
    void formatsPositionOptionAndGoCommands() {
        assertEquals("position startpos", UciFormatter.position(Position.startpos()));
        assertEquals(
                "position startpos moves e2e4 e7e5 g1f3",
                UciFormatter.position(Position.startpos("e2e4", "e7e5", "g1f3")));
        assertEquals(
                "position fen rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1 moves c7c5",
                UciFormatter.position(Position.fen(
                        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
                        "c7c5")));

        assertEquals("setoption name Hash value 128", UciFormatter.setOption("Hash", Optional.of("128")));
        assertEquals("setoption name SyzygyPath value <empty>", UciFormatter.setOption("SyzygyPath", Optional.of("")));
        assertEquals("setoption name Clear Hash", UciFormatter.setOption("Clear Hash", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> UciFormatter.setOption("Hash\nquit", Optional.of("16")));
        assertThrows(IllegalArgumentException.class, () -> UciFormatter.setOption("Hash", Optional.of("16\r\nquit")));

        Limit complexLimit = Limit.builder()
                .searchMoves(List.of("e2e4", "d2d4"))
                .ponder(true)
                .whiteTime(Duration.ofSeconds(60))
                .blackTime(Duration.ofSeconds(55))
                .whiteIncrement(Duration.ofSeconds(1))
                .blackIncrement(Duration.ofSeconds(1))
                .movesToGo(20)
                .depth(15)
                .nodes(100_000L)
                .build();
        assertEquals(
                "go searchmoves e2e4 d2d4 ponder wtime 60000 btime 55000 winc 1000 binc 1000 movestogo 20 depth 15 nodes 100000",
                UciFormatter.go(complexLimit));
    }

    @Test
    void parsesAllOptionVariantsAndMultiWordNames() {
        var checkMsg = assertInstanceOf(
                UciMessage.OptionParsed.class,
                UciParser.parse("option name Ponder type check default true"));
        assertEquals(new EngineOption.Check("Ponder", true), checkMsg.option());

        var spinMsg = assertInstanceOf(
                UciMessage.OptionParsed.class,
                UciParser.parse("option name Move Overhead type spin default 10 min 0 max 5000"));
        assertEquals(new EngineOption.Spin("Move Overhead", 10, 0, 5000), spinMsg.option());

        var comboMsg = assertInstanceOf(
                UciMessage.OptionParsed.class,
                UciParser.parse("option name Playing Style type combo default Normal Play var Solid Play var Normal Play var Sharp Play"));
        assertEquals(
                new EngineOption.Combo("Playing Style", Optional.of("Normal Play"), List.of("Solid Play", "Normal Play", "Sharp Play")),
                comboMsg.option());

        var buttonMsg = assertInstanceOf(
                UciMessage.OptionParsed.class,
                UciParser.parse("option name Clear Hash type button"));
        assertEquals(new EngineOption.Button("Clear Hash"), buttonMsg.option());

        var emptyStringMsg = assertInstanceOf(
                UciMessage.OptionParsed.class,
                UciParser.parse("option name SyzygyPath type string default <empty>"));
        assertEquals(new EngineOption.StringOption("SyzygyPath", Optional.of("")), emptyStringMsg.option());

        assertInstanceOf(
                UciMessage.Unknown.class,
                UciParser.parse("option name BrokenSpin type spin default 50 min 100 max 10"));
    }

    @Test
    void parsesComplexInfoWithUnknownAndMalformedTokensWithoutFailing() {
        var parsed = assertInstanceOf(
                UciMessage.InfoParsed.class,
                UciParser.parse(
                        "info depth 18 seldepth 26 multipv 1 score cp 45 lowerbound wdl 410 550 40 "
                                + "nodes 1234567 nps 2500000 hashfull 412 tbhits 99 time 493 "
                                + "pv e2e4 e7e5 g1f3 string NNUE evaluation active depth 999"));

        Info info = parsed.info();
        assertEquals(Optional.of(18), info.depth());
        assertEquals(Optional.of(26), info.seldepth());
        assertEquals(Optional.of(1), info.multipv());
        assertEquals(Optional.of(Score.cp(45, Score.Bound.LOWERBOUND)), info.score());
        assertEquals(Optional.of(1_234_567L), info.nodes());
        assertEquals(Optional.of(2_500_000L), info.nps());
        assertEquals(Optional.of(412), info.hashfull());
        assertEquals(Optional.of(99L), info.tbhits());
        assertEquals(Optional.of(Duration.ofMillis(493)), info.time());
        assertEquals(List.of("e2e4", "e7e5", "g1f3"), info.pv());
        assertEquals(Optional.of("NNUE evaluation active depth 999"), info.string());

        var malformed = assertInstanceOf(
                UciMessage.InfoParsed.class,
                UciParser.parse("info depth notANumber unknownFlag xyz nodes 800 score mate -4 upperbound currmove e7e8q currmovenumber 2 seldepth"));
        Info recovered = malformed.info();
        assertTrue(recovered.depth().isEmpty());
        assertTrue(recovered.seldepth().isEmpty());
        assertEquals(Optional.of(800L), recovered.nodes());
        assertEquals(Optional.of(Score.mate(-4, Score.Bound.UPPERBOUND)), recovered.score());
        assertEquals(Optional.of("e7e8q"), recovered.currmove());
        assertEquals(Optional.of(2), recovered.currmovenumber());
    }

    @Test
    void parsesBestMoveNormalNullAndMalformedLines() {
        var normal = assertInstanceOf(
                UciMessage.BestMoveParsed.class,
                UciParser.parse("bestmove e2e4 ponder e7e5"));
        assertEquals(Optional.of("e2e4"), normal.bestMove());
        assertEquals(Optional.of("e7e5"), normal.ponderMove());

        var mated = assertInstanceOf(
                UciMessage.BestMoveParsed.class,
                UciParser.parse("bestmove (none)"));
        assertTrue(mated.bestMove().isEmpty());
        assertTrue(mated.ponderMove().isEmpty());

        assertInstanceOf(UciMessage.Unknown.class, UciParser.parse("bestmove"));
        assertInstanceOf(UciMessage.UciOk.class, UciParser.parse("  prefix_noise uciok  "));
    }
}
