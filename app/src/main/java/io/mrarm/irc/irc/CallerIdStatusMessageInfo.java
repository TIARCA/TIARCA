package io.mrarm.irc.irc;

import java.util.Date;

import io.mrarm.chatlib.dto.StatusMessageInfo;

/** Structured caller-id (+g) request shown in the Server tab. */
public class CallerIdStatusMessageInfo extends StatusMessageInfo {
    private final String nick;
    private final String source;

    public CallerIdStatusMessageInfo(String nick, String source) {
        super(null, new Date(), MessageType.NOTICE,
                nick + " vuole scriverti, ma hai attiva la modalità +g.");
        this.nick = nick;
        this.source = source;
    }

    public String getNick() {
        return nick;
    }

    public String getSource() {
        return source;
    }
}
