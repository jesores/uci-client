package io.github.jesores.uci.protocol;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class UciTokenizer {

    private static final Logger LOGGER = System.getLogger(UciTokenizer.class.getName());

    private UciTokenizer() {}

    record TokenSlice(String text, int start, int end) {}

    static List<TokenSlice> tokenize(String line) {
        List<TokenSlice> result = new ArrayList<>();
        int length = line.length();
        int i = 0;
        while (i < length) {
            while (i < length && Character.isWhitespace(line.charAt(i))) {
                i++;
            }
            if (i >= length) {
                break;
            }
            int start = i;
            while (i < length && !Character.isWhitespace(line.charAt(i))) {
                i++;
            }
            result.add(new TokenSlice(line.substring(start, i), start, i));
        }
        return result;
    }

    static int indexOfToken(List<TokenSlice> tokens, String target, int fromIndex) {
        for (int i = Math.max(0, fromIndex); i < tokens.size(); i++) {
            if (target.equals(tokens.get(i).text())) {
                return i;
            }
        }
        return -1;
    }

    static String joinRange(String rawLine, List<TokenSlice> tokens, int fromInclusive, int toExclusive) {
        int startOffset = tokens.get(fromInclusive).start();
        int endOffset = tokens.get(toExclusive - 1).end();
        return rawLine.substring(startOffset, endOffset).trim();
    }

    static Optional<Integer> parseOptionalInt(List<TokenSlice> args, int idx, String field) {
        if (idx >= args.size()) {
            LOGGER.log(Level.DEBUG, () -> "Missing integer value for info field '" + field + "'");
            return Optional.empty();
        }
        String raw = args.get(idx).text();
        try {
            return Optional.of(Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.DEBUG, () -> "Invalid integer '" + raw + "' for info field '" + field + "'");
            return Optional.empty();
        }
    }

    static Optional<Long> parseOptionalLong(List<TokenSlice> args, int idx, String field) {
        if (idx >= args.size()) {
            LOGGER.log(Level.DEBUG, () -> "Missing long value for info field '" + field + "'");
            return Optional.empty();
        }
        String raw = args.get(idx).text();
        try {
            return Optional.of(Long.parseLong(raw));
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.DEBUG, () -> "Invalid long '" + raw + "' for info field '" + field + "'");
            return Optional.empty();
        }
    }
}
