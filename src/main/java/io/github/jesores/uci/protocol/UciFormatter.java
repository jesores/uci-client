package io.github.jesores.uci.protocol;

import io.github.jesores.uci.model.Limit;
import io.github.jesores.uci.model.Position;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

final class UciFormatter {

    private UciFormatter() {}

    static String uci() {
        return "uci";
    }

    static String isReady() {
        return "isready";
    }

    static String uciNewGame() {
        return "ucinewgame";
    }

    static String stop() {
        return "stop";
    }

    static String quit() {
        return "quit";
    }

    static String setOption(String name, Optional<String> value) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        String trimmedName = name.trim();
        if (trimmedName.isEmpty() || containsLineBreak(trimmedName)) {
            throw new IllegalArgumentException("Option name must not be blank or contain line breaks");
        }
        if (value.isPresent()) {
            String rawValue = value.get();
            if (containsLineBreak(rawValue)) {
                throw new IllegalArgumentException("Option value must not contain line breaks");
            }
            String formattedValue = rawValue.isEmpty() ? "<empty>" : rawValue;
            return "setoption name " + trimmedName + " value " + formattedValue;
        }
        return "setoption name " + trimmedName;
    }

    static String position(Position position) {
        Objects.requireNonNull(position, "position must not be null");
        StringBuilder sb = new StringBuilder("position ");
        if (position.fen().isPresent()) {
            sb.append("fen ").append(position.fen().get().trim());
        } else {
            sb.append("startpos");
        }
        if (!position.moves().isEmpty()) {
            sb.append(" moves");
            for (String move : position.moves()) {
                sb.append(' ').append(move);
            }
        }
        return sb.toString();
    }

    static String go(Limit limit) {
        Objects.requireNonNull(limit, "limit must not be null");
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add("go");

        if (!limit.searchMoves().isEmpty()) {
            joiner.add("searchmoves");
            for (String move : limit.searchMoves()) {
                joiner.add(move);
            }
        }
        if (limit.ponder()) {
            joiner.add("ponder");
        }
        limit.whiteTime().ifPresent(d -> joiner.add("wtime " + d.toMillis()));
        limit.blackTime().ifPresent(d -> joiner.add("btime " + d.toMillis()));
        limit.whiteIncrement().ifPresent(d -> joiner.add("winc " + d.toMillis()));
        limit.blackIncrement().ifPresent(d -> joiner.add("binc " + d.toMillis()));
        limit.movesToGo().ifPresent(m -> joiner.add("movestogo " + m));
        limit.depth().ifPresent(d -> joiner.add("depth " + d));
        limit.nodes().ifPresent(n -> joiner.add("nodes " + n));
        limit.mate().ifPresent(m -> joiner.add("mate " + m));
        limit.moveTime().ifPresent(d -> joiner.add("movetime " + d.toMillis()));
        if (limit.isInfinite()) {
            joiner.add("infinite");
        }

        return joiner.toString();
    }

    private static boolean containsLineBreak(String text) {
        return text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
    }
}
