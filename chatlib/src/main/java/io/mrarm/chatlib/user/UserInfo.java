package io.mrarm.chatlib.user;

import java.util.*;

public class UserInfo {

    private UUID uuid;
    private String currentNick;
    private boolean connected = false;
    private boolean away = false;
    private String awayMessage;
    private HashSet<String> channels = new HashSet<>();

    public UserInfo(UUID uuid, String nick) {
        this.uuid = uuid;
        this.currentNick = nick;
    }

    public UserInfo(UserInfo userInfo) {
        this.uuid = userInfo.uuid;
        this.currentNick = userInfo.currentNick;
        this.connected = userInfo.connected;
        this.away = userInfo.away;
        this.awayMessage = userInfo.awayMessage;
        this.channels = (HashSet<String>) userInfo.channels.clone();
    }

    void setCurrentNick(String nick) {
        currentNick = nick;
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getCurrentNick() {
        return currentNick;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isAway() {
        return away;
    }

    public String getAwayMessage() {
        return awayMessage;
    }

    public Set<String> getChannels() {
        return channels;
    }

    void setChannelPresence(String channel, boolean present) {
        if (present)
            channels.add(channel);
        else
            channels.remove(channel);
    }

    void clearChannelPresences() {
        channels.clear();
    }

    void setAway(boolean away, String message) {
        this.away = away;
        this.awayMessage = away ? message : null;
    }

}
