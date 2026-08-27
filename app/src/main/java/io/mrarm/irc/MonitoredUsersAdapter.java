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
        ServerConnectionApi api = connection.getApiInstance() instanceof ServerConnectionApi
                ? (ServerConnectionApi) connection.getApiInstance() : null;
        if (api == null || !manager.isSupported(api.getServerConnectionData()))
            return new Status(context.getString(R.string.monitor_status_unavailable), R.color.appThemeTextColorSecondary);
        if (manager.getSyncState() != MonitoredUsersManager.SyncState.READY)
            return new Status(context.getString(R.string.monitor_status_unavailable), R.color.appThemeTextColorSecondary);
        if (manager.getAliasesOverLimit(user).size() == manager.getAliases(user).size())
            return new Status(context.getString(R.string.monitor_status_over_limit), R.color.serverListInactive);
        List<ServerConfigData.MonitoredAlias> overLimit = manager.getAliasesOverLimit(user);
        List<ServerConfigData.MonitoredAlias> online = manager.getOnlineAliases(user);
        if (online.isEmpty() && !overLimit.isEmpty())
            return new Status(context.getString(R.string.monitor_status_partial_unavailable),
                    R.color.serverListInactive);
        if (online.isEmpty())
            return new Status(context.getString(R.string.monitor_status_offline), R.color.serverListDisconnected);
        String suffix = overLimit.isEmpty() ? "" :
                " · " + context.getString(R.string.monitor_status_some_aliases_over_limit);
        if (online.size() > 1) {
            boolean allKnownAway = true;
            for (ServerConfigData.MonitoredAlias alias : online) {
                UserInfo known = getKnownUser(alias.nick);
                if (known == null || !known.isAway()) { allKnownAway = false; break; }
            }
            String count = context.getResources().getQuantityString(
                    R.plurals.monitor_status_aliases_online, online.size(), online.size());
            return new Status((allKnownAway ? context.getString(R.string.monitor_status_away) +
                    " · " : "") + count + suffix, allKnownAway ? R.color.userAwayColorPrimary :
                    R.color.serverListConnected);
        }
        ServerConfigData.MonitoredAlias active = online.get(0);
        UserInfo known = getKnownUser(active.nick);
        if (known != null && known.isAway()) {
            String status = active.nick.equals(user.nick) ?
                    context.getString(R.string.monitor_status_away) :
                    context.getString(R.string.monitor_status_away_as, active.nick);
            if (known.getAwayMessage() != null && !known.getAwayMessage().isEmpty())
                status += " · " + known.getAwayMessage();
            return new Status(status + suffix, R.color.userAwayColorPrimary);
        }
        String status = active.nick.equals(user.nick) ? context.getString(R.string.monitor_status_online) :
                context.getString(R.string.monitor_status_online_as, active.nick);
        return new Status(status + suffix, R.color.serverListConnected);
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
