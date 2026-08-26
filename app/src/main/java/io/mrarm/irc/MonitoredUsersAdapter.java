package io.mrarm.irc;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.simple_list_item, parent, false));
    }

    @Override public void onBindViewHolder(Holder holder, int position) {
        holder.bind(manager.getMonitoredUsers().get(position));
    }

    @Override public int getItemCount() { return manager.getMonitoredUsers().size(); }

    private String getStatus(Context context, ServerConfigData.MonitoredUser user) {
        ServerConnectionApi api = connection.getApiInstance() instanceof ServerConnectionApi
                ? (ServerConnectionApi) connection.getApiInstance() : null;
        if (api == null || !manager.isSupported(api.getServerConnectionData()))
            return context.getString(R.string.monitor_status_unavailable);
        if (manager.getSyncState() != MonitoredUsersManager.SyncState.READY)
            return context.getString(R.string.monitor_status_unavailable);
        if (!manager.isSynchronizedWithServer(user))
            return context.getString(R.string.monitor_status_over_limit);
        if (!user.online)
            return context.getString(R.string.monitor_status_offline);
        UserInfo known = getKnownUser(user.currentNick == null ? user.nick : user.currentNick);
        if (known != null && known.isAway()) {
            String status = context.getString(R.string.monitor_status_away);
            return known.getAwayMessage() == null || known.getAwayMessage().isEmpty() ? status :
                    status + " — " + known.getAwayMessage();
        }
        return context.getString(R.string.monitor_status_online);
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
        private final TextView text;
        private ServerConfigData.MonitoredUser user;

        Holder(View itemView) {
            super(itemView);
            text = (TextView) itemView;
            text.setOnClickListener(v -> {
                if (user != null)
                    ((MonitoredUsersActivity) v.getContext()).openPrivateConversation(user.currentNick == null ? user.nick : user.currentNick);
            });
            text.setOnLongClickListener(v -> {
                if (user != null)
                    MonitoredUserDialog.show(v.getContext(), connection, user, MonitoredUsersAdapter.this::notifyDataSetChanged);
                return true;
            });
        }

        void bind(ServerConfigData.MonitoredUser value) {
            user = value;
            String nick = value.currentNick == null ? value.nick : value.currentNick;
            text.setText(nick + "\n" + getStatus(text.getContext(), value));
            text.setTextColor(StyledAttributesHelper.getColor(text.getContext(), android.R.attr.textColorPrimary, Color.BLACK));
        }
    }
}
