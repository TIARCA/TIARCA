package io.mrarm.irc.irc;

import java.util.Date;

import io.mrarm.chatlib.dto.StatusMessageInfo;

/** Structured WHOWAS record rendered specially in the Server tab. */
public class WhowasStatusMessageInfo extends StatusMessageInfo {
    private final WhowasCommandHandler.Result result;

    public WhowasStatusMessageInfo(WhowasCommandHandler.Result result) {
        super(null, new Date(), MessageType.NOTICE, "WHOWAS " + result.nick);
        this.result = result;
    }

    public WhowasCommandHandler.Result getResult() {
        return result;
    }
}
