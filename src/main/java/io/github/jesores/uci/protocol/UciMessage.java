package io.github.jesores.uci.protocol;

import io.github.jesores.uci.model.EngineOption;
import io.github.jesores.uci.model.Info;
import java.util.Optional;

sealed interface UciMessage
        permits UciMessage.IdName,
                UciMessage.IdAuthor,
                UciMessage.UciOk,
                UciMessage.ReadyOk,
                UciMessage.OptionParsed,
                UciMessage.InfoParsed,
                UciMessage.BestMoveParsed,
                UciMessage.Unknown {

    record IdName(String name) implements UciMessage {}

    record IdAuthor(String author) implements UciMessage {}

    record UciOk() implements UciMessage {}

    record ReadyOk() implements UciMessage {}

    record OptionParsed(EngineOption option) implements UciMessage {}

    record InfoParsed(Info info) implements UciMessage {}

    record BestMoveParsed(Optional<String> bestMove, Optional<String> ponderMove) implements UciMessage {}

    record Unknown(String rawLine) implements UciMessage {}
}
