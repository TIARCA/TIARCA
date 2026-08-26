package io.mrarm.irc.irc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.irc.config.ServerConfigData;

/** Standard IRC MONITOR protocol state, deliberately independent from UI and notifications. */
public final class MonitoredUsersManager implements CommandHandler {
    public static final int RPL_MONONLINE = 730, RPL_MONOFFLINE = 731, RPL_MONLIST = 732,
            RPL_ENDOFMONLIST = 733, ERR_MONLISTFULL = 734;
    private final ServerConfigData config;
    private String lastError;

    public MonitoredUsersManager(ServerConfigData config) { this.config = config; }
    @Override public Object[] getHandledCommands() { return new Object[] {730,731,732,733,734}; }
    public boolean isSupported(ServerConnectionData data) { return data.getSupportList().getMonitorLimit() >= 0; }
    public int getLimit(ServerConnectionData data) { return data.getSupportList().getMonitorLimit(); }
    public String getLastError() { return lastError; }
    public List<ServerConfigData.MonitoredUser> getMonitoredUsers() {
        return config.monitoredUsers == null ? Collections.emptyList() : Collections.unmodifiableList(config.monitoredUsers);
    }
    public boolean isMonitored(String nick) { return find(nick) != null; }
    public ServerConfigData.MonitoredUser addMonitoredUser(String nick, boolean notifyOnline, boolean notifyOffline) {
        if (nick == null || nick.trim().isEmpty()) throw new IllegalArgumentException("nick");
        ServerConfigData.MonitoredUser existing = find(nick);
        if (existing != null) { existing.notifyOnline = notifyOnline; existing.notifyOffline = notifyOffline; return existing; }
        if (config.monitoredUsers == null) config.monitoredUsers = new ArrayList<>();
        ServerConfigData.MonitoredUser user = new ServerConfigData.MonitoredUser();
        user.nick = nick.trim(); user.currentNick = user.nick; user.notifyOnline = notifyOnline; user.notifyOffline = notifyOffline;
        config.monitoredUsers.add(user); return user;
    }
    public boolean removeMonitoredUser(String nick) { ServerConfigData.MonitoredUser user = find(nick); return user != null && config.monitoredUsers.remove(user); }
    public void updateNotificationPreferences(String nick, boolean online, boolean offline) {
        ServerConfigData.MonitoredUser user = find(nick); if (user == null) throw new IllegalArgumentException("nick");
        user.notifyOnline = online; user.notifyOffline = offline;
    }
    public void synchronize(ServerConnectionData data) {
        lastError = null;
        if (!isSupported(data)) { lastError = "This server does not support MONITOR."; return; }
        int limit = getLimit(data); StringBuilder nicks = new StringBuilder(); int count = 0;
        for (ServerConfigData.MonitoredUser user : getMonitoredUsers()) {
            String nick = user.currentNick == null ? user.nick : user.currentNick;
            if (nick == null || count >= limit || contains(nicks, nick)) continue;
            if (nicks.length() > 0) nicks.append(','); nicks.append(nick); count++;
        }
        try { if (nicks.length() > 0) data.getApi().sendCommand("MONITOR", false, "+", nicks.toString()); data.getApi().sendCommand("MONITOR", false, "S"); }
        catch (IOException ignored) { }
    }
    public void list(ServerConnectionData data) throws IOException { data.getApi().sendCommand("MONITOR", false, "L"); }
    public void clear(ServerConnectionData data) throws IOException { data.getApi().sendCommand("MONITOR", false, "C"); }
    @Override public void handle(ServerConnectionData data, MessagePrefix sender, String command, List<String> params, java.util.Map<String,String> tags) throws InvalidMessageException {
        int numeric = CommandHandler.toNumeric(command);
        if (numeric == ERR_MONLISTFULL) { lastError = "The server MONITOR limit has been reached."; return; }
        if (numeric == RPL_ENDOFMONLIST) return;
        if (numeric != RPL_MONONLINE && numeric != RPL_MONOFFLINE && numeric != RPL_MONLIST) return;
        String raw = CommandHandler.getParamOrNull(params, params.size() - 1); if (raw == null) return;
        boolean online = numeric != RPL_MONOFFLINE;
        for (String item : raw.split(",")) { int bang = item.indexOf('!'); String nick = bang < 0 ? item : item.substring(0,bang); ServerConfigData.MonitoredUser user = find(nick); if (user != null) { user.currentNick = nick; user.online = online; } }
    }
    public void onNickChanged(ServerConnectionData data, String oldNick, String newNick) {
        ServerConfigData.MonitoredUser user = find(oldNick); if (user == null) return; user.currentNick = newNick;
        if (!isSupported(data)) return;
        try { data.getApi().sendCommand("MONITOR", false, "-", oldNick); data.getApi().sendCommand("MONITOR", false, "+", newNick); } catch (IOException ignored) { }
    }
    private ServerConfigData.MonitoredUser find(String nick) { if (nick == null) return null; for (ServerConfigData.MonitoredUser u : getMonitoredUsers()) if ((u.currentNick != null && u.currentNick.equalsIgnoreCase(nick)) || (u.nick != null && u.nick.equalsIgnoreCase(nick))) return u; return null; }
    private static boolean contains(StringBuilder list, String nick) { for (String item : list.toString().split(",")) if (item.equalsIgnoreCase(nick)) return true; return false; }
}
