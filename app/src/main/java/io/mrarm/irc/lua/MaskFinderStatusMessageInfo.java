package io.mrarm.irc.lua;

import java.util.Date;
import io.mrarm.chatlib.dto.StatusMessageInfo;

/**
 * A local-only server-tab row. The nick and continuation controls are
 * structured metadata, never parsed from untrusted IRC message text.
 */
public final class MaskFinderStatusMessageInfo extends StatusMessageInfo {
    public enum Kind { HEADER, USER, CONTINUE, INFO }

    public final Kind kind;
    public final String channel;
    public final String nick;
    public final long searchId;
    public volatile boolean consumed;

    public MaskFinderStatusMessageInfo(Kind kind, String channel, String nick,
                                       long searchId, String text) {
        super(null, new Date(), MessageType.NOTICE, text);
        this.kind = kind;
        this.channel = channel;
        this.nick = nick;
        this.searchId = searchId;
    }
}
