package io.mrarm.irc;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.UUID;

import io.mrarm.chatlib.irc.ServerConnectionApi;
import io.mrarm.irc.irc.MonitoredUsersManager;

public class MonitoredUsersActivity extends ThemedActivity implements MonitoredUsersManager.Listener {
    public static final String ARG_SERVER_UUID = "server_uuid";
    private ServerConnectionInfo connection;
    private MonitoredUsersAdapter adapter;
    private TextView unsupportedNotice;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitored_users);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        String rawUuid = getIntent().getStringExtra(ARG_SERVER_UUID);
        if (rawUuid == null) { finish(); return; }
        connection = ServerConnectionManager.getInstance(this).getConnection(UUID.fromString(rawUuid));
        if (connection == null) { finish(); return; }
        setTitle(getString(R.string.title_activity_monitored_users_network, connection.getName()));
        LinearLayoutManager layout = new LinearLayoutManager(this);
        RecyclerView list = findViewById(R.id.items);
        list.setLayoutManager(layout);
        list.addItemDecoration(new DividerItemDecoration(this, layout.getOrientation()));
        adapter = new MonitoredUsersAdapter(this, connection);
        list.setAdapter(adapter);
        unsupportedNotice = findViewById(R.id.unsupported_notice);
        findViewById(R.id.add).setOnClickListener(v -> MonitoredUserDialog.show(this, connection, null,
                adapter::notifyDataSetChanged));
        connection.getMonitoredUsersManager().addListener(this);
        updateSupportNotice();
    }

    @Override protected void onDestroy() {
        if (connection != null) connection.getMonitoredUsersManager().removeListener(this);
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
        connection.addStoredConversation(nick);
        startActivity(MainActivity.getLaunchIntent(this, connection, nick));
    }

    private void updateSupportNotice() {
        boolean supported = connection.getApiInstance() instanceof ServerConnectionApi &&
                connection.getMonitoredUsersManager().isSupported(
                        ((ServerConnectionApi) connection.getApiInstance()).getServerConnectionData());
        unsupportedNotice.setVisibility(supported ? View.GONE : View.VISIBLE);
        if (!supported) unsupportedNotice.setText(R.string.monitor_server_unsupported);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
