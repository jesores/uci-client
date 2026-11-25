package io.github.jesores.uci.protocol;

import io.github.jesores.uci.model.EngineOption;
import io.github.jesores.uci.protocol.UciTokenizer.TokenSlice;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class UciOptionParser {

    private static final Logger LOGGER = System.getLogger(UciOptionParser.class.getName());

    private UciOptionParser() {}

    static UciMessage parse(String rawLine, List<TokenSlice> args) {
        int namePos = UciTokenizer.indexOfToken(args, "name", 0);
        int typePos = UciTokenizer.indexOfToken(args, "type", namePos + 1);
        if (namePos < 0 || typePos < 0 || typePos <= namePos + 1 || typePos + 1 >= args.size()) {
            LOGGER.log(Level.WARNING, () -> "Malformed option declaration: " + rawLine);
            return new UciMessage.Unknown(rawLine);
        }

        String name = UciTokenizer.joinRange(rawLine, args, namePos + 1, typePos);
        String type = args.get(typePos + 1).text().toLowerCase(Locale.ROOT);
        List<TokenSlice> tail = args.subList(typePos + 2, args.size());

        try {
            EngineOption parsed = switch (type) {
                case "check" -> parseCheckOption(name, tail);
                case "spin" -> parseSpinOption(name, tail);
                case "combo" -> parseComboOption(rawLine, name, tail);
                case "button" -> new EngineOption.Button(name);
                case "string" -> parseStringOption(rawLine, name, tail);
                default -> null;
            };
            if (parsed == null) {
                LOGGER.log(Level.WARNING, () -> "Unsupported or incomplete option line: " + rawLine);
                return new UciMessage.Unknown(rawLine);
            }
            return new UciMessage.OptionParsed(parsed);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, () -> "Ignoring malformed option line '" + rawLine + "': " + ex.getMessage());
            return new UciMessage.Unknown(rawLine);
        }
    }

    private static EngineOption parseCheckOption(String name, List<TokenSlice> tail) {
        boolean defaultValue = false;
        for (int i = 0; i + 1 < tail.size(); i++) {
            if ("default".equals(tail.get(i).text())) {
                defaultValue = Boolean.parseBoolean(tail.get(i + 1).text());
                break;
            }
        }
        return new EngineOption.Check(name, defaultValue);
    }

    private static EngineOption parseSpinOption(String name, List<TokenSlice> tail) {
        Long defaultVal = null;
        Long minVal = null;
        Long maxVal = null;
        for (int i = 0; i + 1 < tail.size(); i++) {
            switch (tail.get(i).text()) {
                case "default" -> {
                    defaultVal = Long.parseLong(tail.get(i + 1).text());
                    i++;
                }
                case "min" -> {
                    minVal = Long.parseLong(tail.get(i + 1).text());
                    i++;
                }
                case "max" -> {
                    maxVal = Long.parseLong(tail.get(i + 1).text());
                    i++;
                }
                default -> {
                }
            }
        }
        if (defaultVal == null || minVal == null || maxVal == null) {
            return null;
        }
        return new EngineOption.Spin(name, defaultVal, minVal, maxVal);
    }

    private static EngineOption parseComboOption(String rawLine, String name, List<TokenSlice> tail) {
        Optional<String> defaultValue = Optional.empty();
        List<String> choices = new ArrayList<>();
        int i = 0;
        while (i < tail.size()) {
            String token = tail.get(i).text();
            if ("default".equals(token)) {
                int end = nextComboKeyword(tail, i + 1);
                if (end > i + 1) {
                    defaultValue = Optional.of(UciTokenizer.joinRange(rawLine, tail, i + 1, end));
                }
                i = end;
            } else if ("var".equals(token)) {
                int end = nextComboKeyword(tail, i + 1);
                if (end > i + 1) {
                    choices.add(UciTokenizer.joinRange(rawLine, tail, i + 1, end));
                }
                i = end;
            } else {
                i++;
            }
        }
        return new EngineOption.Combo(name, defaultValue, choices);
    }

    private static int nextComboKeyword(List<TokenSlice> tokens, int from) {
        for (int i = from; i < tokens.size(); i++) {
            String text = tokens.get(i).text();
            if ("var".equals(text) || "default".equals(text)) {
                return i;
            }
        }
        return tokens.size();
    }

    private static EngineOption parseStringOption(String rawLine, String name, List<TokenSlice> tail) {
        int defaultIdx = UciTokenizer.indexOfToken(tail, "default", 0);
        if (defaultIdx < 0) {
            return new EngineOption.StringOption(name, Optional.empty());
        }
        if (defaultIdx + 1 >= tail.size()) {
            return new EngineOption.StringOption(name, Optional.of(""));
        }
        String rawValue = rawLine.substring(tail.get(defaultIdx + 1).start()).trim();
        if ("<empty>".equals(rawValue)) {
            return new EngineOption.StringOption(name, Optional.of(""));
        }
        return new EngineOption.StringOption(name, Optional.of(rawValue));
    }
}
