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
    MonitoredUsersAdapter(Context context, ServerConnectionInfo connection) {
        this.connection = connection;
        this.manager = connection.getMonitoredUsersManager();
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
        if (!manager.isSynchronizedWithServer(user))
            return new Status(context.getString(R.string.monitor_status_over_limit), R.color.serverListInactive);
        if (!user.online)
            return new Status(context.getString(R.string.monitor_status_offline), R.color.serverListDisconnected);
        UserInfo known = getKnownUser(user.currentNick == null ? user.nick : user.currentNick);
        if (known != null && known.isAway()) {
            String status = context.getString(R.string.monitor_status_away);
            return new Status(known.getAwayMessage() == null || known.getAwayMessage().isEmpty() ? status :
                    status + " · " + known.getAwayMessage(), R.color.userAwayColorPrimary);
        }
        return new Status(context.getString(R.string.monitor_status_online), R.color.serverListConnected);
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
                if (user != null)
                    ((MonitoredUsersActivity) v.getContext()).openPrivateConversation(user.currentNick == null ? user.nick : user.currentNick);
            });
            itemView.setOnLongClickListener(v -> {
                if (user != null)
                    MonitoredUserDialog.show(v.getContext(), connection, user, MonitoredUsersAdapter.this::notifyDataSetChanged);
                return true;
            });
        }

        void bind(ServerConfigData.MonitoredUser value) {
            user = value;
            String nick = value.currentNick == null ? value.nick : value.currentNick;
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
