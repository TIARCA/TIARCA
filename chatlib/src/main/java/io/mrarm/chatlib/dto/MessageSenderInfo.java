package io.mrarm.chatlib.dto;

import java.util.UUID;

public class MessageSenderInfo extends NickWithPrefix {

    private String user;
    private String host;
    private UUID userUUID;
    private boolean automated;

    public MessageSenderInfo(String nick, String user, String host, NickPrefixList nickPrefixes,
                             UUID userUUID) {
        this(nick, user, host, nickPrefixes, userUUID, false);
    }

    public MessageSenderInfo(String nick, String user, String host, NickPrefixList nickPrefixes,
                             UUID userUUID, boolean automated) {
        super(nick, nickPrefixes);
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

    public boolean isAutomated() {
        return automated;
    }

}
