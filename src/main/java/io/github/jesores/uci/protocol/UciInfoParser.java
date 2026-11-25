package io.github.jesores.uci.protocol;

import io.github.jesores.uci.model.Info;
import io.github.jesores.uci.model.Score;
import io.github.jesores.uci.protocol.UciTokenizer.TokenSlice;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class UciInfoParser {

    private static final Logger LOGGER = System.getLogger(UciInfoParser.class.getName());

    private static final Set<String> KNOWN_INFO_KEYWORDS = Set.of(
            "depth",
            "seldepth",
            "time",
            "nodes",
            "pv",
            "multipv",
            "score",
            "currmove",
            "currmovenumber",
            "hashfull",
            "nps",
            "tbhits",
            "sbhits",
            "cpuload",
            "string",
            "refutation",
            "currline",
            "wdl");

    private UciInfoParser() {}

    static UciMessage parse(String rawLine, List<TokenSlice> args) {
        Info.Builder builder = Info.builder();
        int i = 0;
        while (i < args.size()) {
            String token = args.get(i).text();
            switch (token) {
                case "string" -> {
                    String text = i + 1 < args.size()
                            ? rawLine.substring(args.get(i + 1).start()).stripTrailing()
                            : "";
                    builder.string(text);
                    i = args.size();
                }
                case "depth" -> {
                    Optional<Integer> val = UciTokenizer.parseOptionalInt(args, i + 1, "depth");
                    val.ifPresent(builder::depth);
                    i += val.isPresent() ? 2 : 1;
                }
                case "seldepth" -> {
                    Optional<Integer> val = UciTokenizer.parseOptionalInt(args, i + 1, "seldepth");
                    val.ifPresent(builder::seldepth);
                    i += val.isPresent() ? 2 : 1;
                }
                case "multipv" -> {
                    Optional<Integer> val = UciTokenizer.parseOptionalInt(args, i + 1, "multipv");
                    val.ifPresent(builder::multipv);
                    i += val.isPresent() ? 2 : 1;
                }
                case "nodes" -> {
                    Optional<Long> val = UciTokenizer.parseOptionalLong(args, i + 1, "nodes");
                    val.ifPresent(builder::nodes);
                    i += val.isPresent() ? 2 : 1;
                }
                case "nps" -> {
                    Optional<Long> val = UciTokenizer.parseOptionalLong(args, i + 1, "nps");
                    val.ifPresent(builder::nps);
                    i += val.isPresent() ? 2 : 1;
                }
                case "hashfull" -> {
                    Optional<Integer> val = UciTokenizer.parseOptionalInt(args, i + 1, "hashfull");
                    val.ifPresent(builder::hashfull);
                    i += val.isPresent() ? 2 : 1;
                }
                case "tbhits" -> {
                    Optional<Long> val = UciTokenizer.parseOptionalLong(args, i + 1, "tbhits");
                    val.ifPresent(builder::tbhits);
                    i += val.isPresent() ? 2 : 1;
                }
                case "time" -> {
                    Optional<Long> val = UciTokenizer.parseOptionalLong(args, i + 1, "time");
                    val.ifPresent(ms -> builder.time(Duration.ofMillis(ms)));
                    i += val.isPresent() ? 2 : 1;
                }
                case "currmove" -> {
                    if (i + 1 < args.size() && !KNOWN_INFO_KEYWORDS.contains(args.get(i + 1).text())) {
                        builder.currmove(args.get(i + 1).text());
                        i += 2;
                    } else {
                        i++;
                    }
                }
                case "currmovenumber" -> {
                    Optional<Integer> val = UciTokenizer.parseOptionalInt(args, i + 1, "currmovenumber");
                    val.ifPresent(builder::currmovenumber);
                    i += val.isPresent() ? 2 : 1;
                }
                case "score" -> i = parseScoreSegment(args, i + 1, builder);
                case "pv" -> {
                    List<String> moves = new ArrayList<>();
                    int j = i + 1;
                    while (j < args.size() && !KNOWN_INFO_KEYWORDS.contains(args.get(j).text())) {
                        moves.add(args.get(j).text());
                        j++;
                    }
                    builder.pv(moves);
                    i = j;
                }
                default -> {
                    String ignored = token;
                    LOGGER.log(Level.TRACE, () -> "Ignoring unrecognized info token: " + ignored);
                    i++;
                }
            }
        }
        return new UciMessage.InfoParsed(builder.build());
    }

    private static int parseScoreSegment(List<TokenSlice> args, int startIdx, Info.Builder builder) {
        Integer cpValue = null;
        Integer mateValue = null;
        Score.Bound bound = Score.Bound.EXACT;
        int i = startIdx;

        while (i < args.size()) {
            String sub = args.get(i).text();
            if ("cp".equals(sub)) {
                Optional<Integer> parsed = UciTokenizer.parseOptionalInt(args, i + 1, "score cp");
                if (parsed.isPresent()) {
                    cpValue = parsed.get();
                    i += 2;
                } else {
                    i++;
                }
            } else if ("mate".equals(sub)) {
                Optional<Integer> parsed = UciTokenizer.parseOptionalInt(args, i + 1, "score mate");
                if (parsed.isPresent()) {
                    mateValue = parsed.get();
                    i += 2;
                } else {
                    i++;
                }
            } else if ("lowerbound".equals(sub)) {
                bound = Score.Bound.LOWERBOUND;
                i++;
            } else if ("upperbound".equals(sub)) {
                bound = Score.Bound.UPPERBOUND;
                i++;
            } else {
                break;
            }
        }

        if (cpValue != null) {
            builder.score(Score.cp(cpValue, bound));
        } else if (mateValue != null) {
            builder.score(Score.mate(mateValue, bound));
        }
        return i;
    }
}
