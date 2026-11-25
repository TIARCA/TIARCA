package io.mrarm.chatlib.dto;

import java.util.UUID;

public class MessageSenderInfo extends NickWithPrefix {

    private String user;
    private String host;
    private UUID userUUID;
    private boolean automated;
    // Transient observation carried by the live message only, not persisted.
    private final boolean liveIdentityObserved;
    private final boolean liveChannelMember;
    private final String observedAccount;

    public MessageSenderInfo(String nick, String user, String host, NickPrefixList nickPrefixes,
                             UUID userUUID) {
        this(nick, user, host, nickPrefixes, userUUID, false);
    }

    public MessageSenderInfo(String nick, String user, String host, NickPrefixList nickPrefixes,
                             UUID userUUID, boolean automated) {
        this(nick, user, host, nickPrefixes, userUUID, automated, false, false, null);
    }

    public MessageSenderInfo(String nick, String user, String host, NickPrefixList nickPrefixes,
                             UUID userUUID, boolean automated, boolean liveIdentityObserved,
                             boolean liveChannelMember, String observedAccount) {
        super(nick, nickPrefixes);
        this.liveIdentityObserved = liveIdentityObserved;
        this.liveChannelMember = liveChannelMember;
        this.observedAccount = observedAccount;
        this.user = user;
        this.host = host;
        this.userUUID = userUUID;
        this.automated = automated;
    }

    public String getUser() {
        return user;
    }

    public String getHost() {
        return host;
    }

    public UUID getUserUUID() {
        return userUUID;
    }

    public boolean hasLiveIdentityObservation() { return liveIdentityObserved; }
    public boolean wasLiveChannelMember() { return liveChannelMember; }
    /** null means unknown, '*' means confirmed logout. */
    public String getObservedAccount() { return observedAccount; }

    public boolean isAutomated() {
        return automated;
    }

}
