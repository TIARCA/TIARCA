package io.mrarm.irc;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;

import java.util.UUID;

import io.mrarm.chatlib.irc.ServerConnectionApi;
import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.config.ServerConfigManager;
import io.mrarm.irc.irc.MonitoredUsersManager;

public class MonitoredUsersActivity extends ThemedActivity implements MonitoredUsersManager.Listener {
    public static final String ARG_SERVER_UUID = "server_uuid";
    private ServerConnectionInfo connection;
    private MonitoredUsersManager monitoredUsers;
    private MonitoredUsersAdapter adapter;
    private TextView unsupportedNotice;

    public static Intent createLaunchIntent(Context context, ServerConnectionInfo connection) {
        if (connection != null && connection.getUUID() != null) {
            return createLaunchIntentForServer(context, connection.getUUID());
        }
        return createLaunchIntentGlobal(context);
    }

    public static Intent createLaunchIntentForServer(Context context, UUID serverUuid) {
        if (serverUuid != null) {
            Intent intent = context != null ? new Intent(context, MonitoredUsersActivity.class) : new Intent();
            if (context == null) {
                intent.setClassName("io.mrarm.irc", MonitoredUsersActivity.class.getName());
            }
            intent.putExtra(ARG_SERVER_UUID, serverUuid.toString());
            return intent;
        }
        return createLaunchIntentGlobal(context);
    }

    public static Intent createLaunchIntentGlobal(Context context) {
        Intent intent = context != null ? new Intent(context, MonitoredServersActivity.class) : new Intent();
        if (context == null) {
            intent.setClassName("io.mrarm.irc", MonitoredServersActivity.class.getName());
        }
        return intent;
    }

    public static void open(Context context, ServerConnectionInfo connection) {
        context.startActivity(createLaunchIntent(context, connection));
    }

    public static void openForServer(Context context, UUID serverUuid) {
        context.startActivity(createLaunchIntentForServer(context, serverUuid));
    }

    public static void openGlobal(Context context) {
        context.startActivity(createLaunchIntentGlobal(context));
    }

    private void fallbackToGlobalList() {
        Intent intent = new Intent(this, MonitoredServersActivity.class);
        startActivity(intent);
        finish();
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitored_users);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        String rawUuid = getIntent().getStringExtra(ARG_SERVER_UUID);
        if (rawUuid == null) {
            fallbackToGlobalList();
            return;
        }
        UUID serverUuid;
        try {
            serverUuid = UUID.fromString(rawUuid);
        } catch (Exception e) {
            fallbackToGlobalList();
            return;
        }
        ServerConfigData config = ServerConfigManager.getInstance(this).findServer(serverUuid);
        if (config == null) {
            fallbackToGlobalList();
            return;
        }
        connection = ServerConnectionManager.getInstance(this).getConnection(serverUuid);
        monitoredUsers = connection != null ? connection.getMonitoredUsersManager() :
                new MonitoredUsersManager(config, () ->
                        ServerConfigManager.getInstance(this).saveServerConfiguration(config));
        setTitle(getString(R.string.title_activity_monitored_users_network, config.name));
        LinearLayoutManager layout = new LinearLayoutManager(this);
        RecyclerView list = findViewById(R.id.items);
        list.setLayoutManager(layout);
        list.addItemDecoration(new DividerItemDecoration(this, layout.getOrientation()));
        adapter = new MonitoredUsersAdapter(this, connection, monitoredUsers);
        list.setAdapter(adapter);
        unsupportedNotice = findViewById(R.id.unsupported_notice);
        View add = findViewById(R.id.add);
        if (connection == null) {
            add.setVisibility(View.GONE);
        } else {
            add.setOnClickListener(v -> MonitoredUserDialog.show(this, connection, null,
                    adapter::notifyDataSetChanged));
        }
        monitoredUsers.addListener(this);
        updateSupportNotice();
    }

    @Override protected void onDestroy() {
        if (monitoredUsers != null) monitoredUsers.removeListener(this);
        super.onDestroy();
    }

    @Override public void onPresenceUpdated(io.mrarm.irc.config.ServerConfigData.MonitoredUser user,
                                            MonitoredUsersManager.PresenceUpdate update) {
        runOnUiThread(() -> adapter.notifyDataSetChanged());
    }

    @Override public void onSyncStateChanged(MonitoredUsersManager.SyncState state) {
        runOnUiThread(() -> { updateSupportNotice(); adapter.notifyDataSetChanged(); });
    }

    @Override public void onMonitoredUserChanged(io.mrarm.irc.config.ServerConfigData.MonitoredUser user) {
        runOnUiThread(() -> adapter.notifyDataSetChanged());
    }

    void openPrivateConversation(String nick) {
        if (nick == null || nick.trim().isEmpty()) return;
        if (connection != null) {
            String target = nick.trim();
            connection.registerPrivateConversation(target, () -> runOnUiThread(() ->
                    startActivity(MainActivity.getLaunchIntent(this, connection, target))));
        }
    }

    private void updateSupportNotice() {
        if (connection == null) {
            unsupportedNotice.setVisibility(View.GONE);
            return;
        }
        boolean supported = connection.getApiInstance() instanceof ServerConnectionApi &&
                monitoredUsers.isSupported(
                        ((ServerConnectionApi) connection.getApiInstance()).getServerConnectionData());
        unsupportedNotice.setVisibility(supported ? View.GONE : View.VISIBLE);
        if (!supported) unsupportedNotice.setText(R.string.monitor_server_unsupported);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
