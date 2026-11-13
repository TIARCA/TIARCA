package io.mrarm.irc.lua;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import io.mrarm.chatlib.dto.StatusMessageInfo;

/**
 * A local-only server-tab row. The nick and continuation controls are
 * structured metadata, never parsed from untrusted IRC message text.
 */
public final class MaskFinderStatusMessageInfo extends StatusMessageInfo {
    public enum Kind { HEADER, BATCH, CONTINUE, INFO }

    public final Kind kind;
    public final String channel;
    public final String nick;
    public final long searchId;
    public volatile boolean consumed;
    public final List<String> nicks;
    public final List<String> masks;

    public MaskFinderStatusMessageInfo(Kind kind, String channel, String nick,
                                       long searchId, String text) {
        super(null, new Date(), MessageType.NOTICE, text);
        this.kind = kind;
        this.channel = channel;
        this.nick = nick;
        this.searchId = searchId;
        this.nicks = Collections.emptyList();
        this.masks = Collections.emptyList();
    }

    private MaskFinderStatusMessageInfo(String channel, long searchId,
                                        List<String> nicks, List<String> masks) {
        super(null, new Date(), MessageType.NOTICE, String.join("\n", masks));
        this.kind = Kind.BATCH;
        this.channel = channel;
        this.nick = null;
        this.searchId = searchId;
        this.nicks = Collections.unmodifiableList(new ArrayList<>(nicks));
        this.masks = Collections.unmodifiableList(new ArrayList<>(masks));
    }

    public static MaskFinderStatusMessageInfo batch(String channel, long searchId,
                                                     List<String> nicks, List<String> masks) {
        if (nicks == null || masks == null || nicks.isEmpty() ||
                nicks.size() != masks.size() || nicks.size() > 50)
            throw new IllegalArgumentException("Invalid Mask Finder block");
        return new MaskFinderStatusMessageInfo(channel, searchId, nicks, masks);
    }
}
