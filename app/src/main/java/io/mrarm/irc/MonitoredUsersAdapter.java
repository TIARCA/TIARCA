package io.mrarm.irc;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.mrarm.chatlib.irc.ServerConnectionApi;
import io.mrarm.chatlib.user.UserInfo;
import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.irc.MonitoredUsersManager;
import io.mrarm.irc.util.StyledAttributesHelper;

final class MonitoredUsersAdapter extends RecyclerView.Adapter<MonitoredUsersAdapter.Holder> {
    private final ServerConnectionInfo connection;
    private final MonitoredUsersManager manager;
    MonitoredUsersAdapter(Context context, ServerConnectionInfo connection,
                          MonitoredUsersManager manager) {
        this.connection = connection;
        this.manager = manager;
    }

    @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.monitored_user_item, parent, false));
    }

    @Override public void onBindViewHolder(Holder holder, int position) {
        holder.bind(manager.getMonitoredUsers().get(position));
    }

    @Override public int getItemCount() { return manager.getMonitoredUsers().size(); }

    private Status getStatus(Context context, ServerConfigData.MonitoredUser user) {
        ServerConnectionApi api = connection != null && connection.getApiInstance() instanceof ServerConnectionApi
                ? (ServerConnectionApi) connection.getApiInstance() : null;
        boolean isSupported = api != null && manager.isSupported(api.getServerConnectionData());
        boolean isReady = manager.getSyncState() == MonitoredUsersManager.SyncState.READY;

        List<ServerConfigData.MonitoredAlias> overLimit = (isSupported && isReady) ?
                manager.getAliasesOverLimit(user) : java.util.Collections.emptyList();
        List<ServerConfigData.MonitoredAlias> online = (isSupported && isReady) ?
                manager.getOnlineAliases(user) : java.util.Collections.emptyList();

        if (isSupported && isReady && overLimit.size() == manager.getAliases(user).size())
            return new Status(context.getString(R.string.monitor_status_over_limit), R.color.serverListInactive);

        if (isSupported && isReady && online.isEmpty() && !overLimit.isEmpty())
            return new Status(context.getString(R.string.monitor_status_partial_unavailable),
                    R.color.serverListInactive);

        String suffix = overLimit.isEmpty() ? "" :
                " · " + context.getString(R.string.monitor_status_some_aliases_over_limit);

        String state = user.lastKnownState;
        if (state == null)
            state = ServerConfigData.MonitoredUser.STATE_UNKNOWN;

        if (ServerConfigData.MonitoredUser.STATE_ONLINE.equals(state) || !online.isEmpty()) {
            String activeNick = !online.isEmpty() ? online.get(0).nick :
                    (user.lastKnownNick != null ? user.lastKnownNick : user.nick);

            UserInfo known = getKnownUser(activeNick);
            boolean isAway = known != null && known.isAway();
            String dateStr = user.lastStateTimestamp > 0 ? formatDate(context, user.lastStateTimestamp) : null;
            String timeStr = user.lastStateTimestamp > 0 ? formatTime(context, user.lastStateTimestamp) : null;

            String statusText;
            if (isAway) {
                if (dateStr != null && timeStr != null) {
                    statusText = activeNick.equals(user.nick) ?
                            context.getString(R.string.monitor_status_away_since, dateStr, timeStr) :
                            context.getString(R.string.monitor_status_away_as_since, activeNick, dateStr, timeStr);
                } else {
                    statusText = activeNick.equals(user.nick) ?
                            context.getString(R.string.monitor_status_away) :
                            context.getString(R.string.monitor_status_away_as, activeNick);
                }
                if (known.getAwayMessage() != null && !known.getAwayMessage().isEmpty())
                    statusText += " · " + known.getAwayMessage();
                return new Status(statusText + suffix, R.color.userAwayColorPrimary);
            }

            if (dateStr != null && timeStr != null) {
                statusText = activeNick.equals(user.nick) ?
                        context.getString(R.string.monitor_status_online_since, dateStr, timeStr) :
                        context.getString(R.string.monitor_status_online_as_since, activeNick, dateStr, timeStr);
            } else {
                statusText = activeNick.equals(user.nick) ?
                        context.getString(R.string.monitor_status_online) :
                        context.getString(R.string.monitor_status_online_as, activeNick);
            }
            return new Status(statusText + suffix, R.color.serverListConnected);
        }

        if (ServerConfigData.MonitoredUser.STATE_OFFLINE.equals(state)) {
            if (user.lastStateTimestamp > 0) {
                String dateStr = formatDate(context, user.lastStateTimestamp);
                String timeStr = formatTime(context, user.lastStateTimestamp);
                return new Status(context.getString(R.string.monitor_status_offline_last_seen, dateStr, timeStr) + suffix,
                        R.color.serverListDisconnected);
            }
            return new Status(context.getString(R.string.monitor_status_offline) + suffix,
                    R.color.serverListDisconnected);
        }

        return new Status(context.getString(R.string.monitor_status_unknown) + suffix,
                R.color.appThemeTextColorSecondary);
    }

    private static String formatDate(Context context, long timestamp) {
        java.text.DateFormat fmt = android.text.format.DateFormat.getDateFormat(context);
        return fmt.format(new java.util.Date(timestamp));
    }

    private static String formatTime(Context context, long timestamp) {
        java.text.DateFormat fmt = android.text.format.DateFormat.getTimeFormat(context);
        return fmt.format(new java.util.Date(timestamp));
    }

    private UserInfo getKnownUser(String nick) {
        if (nick == null || connection.getApiInstance() == null)
            return null;
        try {
            return connection.getApiInstance().getUserInfoApi().getUser(nick, null, null, null, null).get();
        } catch (Exception ignored) {
            return null;
        }
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final TextView nickname;
        private final TextView status;
        private final ImageView notifications;
        private ServerConfigData.MonitoredUser user;

        Holder(View itemView) {
            super(itemView);
            nickname = itemView.findViewById(R.id.nickname);
            status = itemView.findViewById(R.id.status);
            notifications = itemView.findViewById(R.id.notifications);
            itemView.setOnClickListener(v -> {
                if (user != null && connection != null)
                    ((MonitoredUsersActivity) v.getContext()).openPrivateConversation(
                            manager.getPreferredNick(user));
            });
            itemView.setOnLongClickListener(v -> {
                if (user != null && connection != null)
                    MonitoredUserDialog.show(v.getContext(), connection, user, MonitoredUsersAdapter.this::notifyDataSetChanged);
                return connection != null;
            });
        }

        void bind(ServerConfigData.MonitoredUser value) {
            user = value;
            String nick = value.nick;
            Status state = getStatus(itemView.getContext(), value);
            nickname.setText(nick);
            nickname.setTextColor(StyledAttributesHelper.getColor(itemView.getContext(), android.R.attr.textColorPrimary, Color.BLACK));
            status.setText(state.text);
            status.setTextColor(ContextCompat.getColor(itemView.getContext(), state.color));
            notifications.setVisibility(value.notifyOnline || value.notifyOffline ? View.VISIBLE : View.GONE);
            itemView.setContentDescription(nick + ", " + state.text +
                    (value.notifyOnline || value.notifyOffline ? ", " +
                            itemView.getContext().getString(R.string.monitor_notifications_enabled) : ""));
        }
    }

    private static final class Status {
        final String text;
        final int color;
        Status(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }
}
