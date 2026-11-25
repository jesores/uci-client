package io.github.jesores.uci.protocol;

import io.github.jesores.uci.protocol.UciTokenizer.TokenSlice;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class UciParser {

    private static final Logger LOGGER = System.getLogger(UciParser.class.getName());

    private static final Set<String> KNOWN_COMMANDS = Set.of(
            "uciok",
            "readyok",
            "id",
            "option",
            "info",
            "bestmove");

    private UciParser() {}

    static UciMessage parse(String rawLine) {
        Objects.requireNonNull(rawLine, "rawLine must not be null");
        if (rawLine.isBlank()) {
            return new UciMessage.Unknown(rawLine);
        }

        List<TokenSlice> tokens = UciTokenizer.tokenize(rawLine);
        int commandIdx = findFirstKnownCommand(tokens);
        if (commandIdx < 0) {
            return new UciMessage.Unknown(rawLine);
        }

        String command = tokens.get(commandIdx).text();
        List<TokenSlice> args = tokens.subList(commandIdx + 1, tokens.size());

        return switch (command) {
            case "uciok" -> new UciMessage.UciOk();
            case "readyok" -> new UciMessage.ReadyOk();
            case "id" -> parseId(rawLine, args);
            case "option" -> UciOptionParser.parse(rawLine, args);
            case "info" -> UciInfoParser.parse(rawLine, args);
            case "bestmove" -> parseBestMove(rawLine, args);
            default -> new UciMessage.Unknown(rawLine);
        };
    }

    private static UciMessage parseId(String rawLine, List<TokenSlice> args) {
        if (args.isEmpty()) {
            return new UciMessage.Unknown(rawLine);
        }
        String kind = args.get(0).text();
        String value = args.size() > 1 ? rawLine.substring(args.get(1).start()).trim() : "";
        return switch (kind) {
            case "name" -> new UciMessage.IdName(value);
            case "author" -> new UciMessage.IdAuthor(value);
            default -> new UciMessage.Unknown(rawLine);
        };
    }

    private static UciMessage parseBestMove(String rawLine, List<TokenSlice> args) {
        if (args.isEmpty()) {
            LOGGER.log(Level.WARNING, () -> "Malformed bestmove without move token: " + rawLine);
            return new UciMessage.Unknown(rawLine);
        }

        Optional<String> bestMove = normalizeMove(args.get(0).text());
        Optional<String> ponderMove = Optional.empty();

        for (int i = 1; i < args.size(); i++) {
            if ("ponder".equals(args.get(i).text()) && i + 1 < args.size()) {
                ponderMove = normalizeMove(args.get(i + 1).text());
                break;
            }
        }
        return new UciMessage.BestMoveParsed(bestMove, ponderMove);
    }

    private static Optional<String> normalizeMove(String token) {
        if ("(none)".equalsIgnoreCase(token) || "0000".equals(token) || "null".equalsIgnoreCase(token)) {
            return Optional.empty();
        }
        return Optional.of(token);
    }

    private static int findFirstKnownCommand(List<TokenSlice> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (KNOWN_COMMANDS.contains(tokens.get(i).text())) {
                return i;
            }
        }
        return -1;
    }
}
