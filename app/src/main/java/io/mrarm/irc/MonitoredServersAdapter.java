package io.mrarm.irc;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.config.ServerConfigManager;

/** Displays the configured servers and counts their persisted monitored users. */
final class MonitoredServersAdapter extends RecyclerView.Adapter<MonitoredServersAdapter.Holder> {
    interface Listener { void onServerSelected(ServerConfigData config); }

    private final Context context;
    private final Listener listener;
    private List<ServerConfigData> servers = Collections.emptyList();

    MonitoredServersAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        reload();
    }

    void reload() {
        servers = ServerConfigManager.getInstance(context).getServers();
        notifyDataSetChanged();
    }

    @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(
                R.layout.monitored_server_item, parent, false));
    }

    @Override public void onBindViewHolder(Holder holder, int position) {
        holder.bind(servers.get(position));
    }

    @Override public int getItemCount() { return servers.size(); }

    final class Holder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView count;
        private ServerConfigData config;

        Holder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.server_name);
            count = itemView.findViewById(R.id.monitored_user_count);
            itemView.setOnClickListener(v -> {
                if (config != null) listener.onServerSelected(config);
            });
        }

        void bind(ServerConfigData value) {
            config = value;
            String serverName = value.name == null || value.name.trim().isEmpty()
                    ? value.address : value.name;
            name.setText(serverName);
            int userCount = value.monitoredUsers == null ? 0 : value.monitoredUsers.size();
            count.setText(context.getResources().getQuantityString(
                    R.plurals.monitored_users_count, userCount, userCount));
        }
    }
}
