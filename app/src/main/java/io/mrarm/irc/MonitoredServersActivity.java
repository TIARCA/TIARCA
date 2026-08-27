package io.mrarm.irc;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Selects the server whose persisted monitored-user list should be displayed. */
public class MonitoredServersActivity extends ThemedActivity {
    private MonitoredServersAdapter adapter;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitored_servers);
        setTitle(R.string.title_activity_monitored_users);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        LinearLayoutManager layout = new LinearLayoutManager(this);
        RecyclerView list = findViewById(R.id.items);
        list.setLayoutManager(layout);
        list.addItemDecoration(new DividerItemDecoration(this, layout.getOrientation()));
        adapter = new MonitoredServersAdapter(this, config -> {
            Intent intent = new Intent(this, MonitoredUsersActivity.class);
            intent.putExtra(MonitoredUsersActivity.ARG_SERVER_UUID, config.uuid.toString());
            startActivity(intent);
        });
        list.setAdapter(adapter);
    }

    @Override protected void onResume() {
        super.onResume();
        if (adapter != null) adapter.reload();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
