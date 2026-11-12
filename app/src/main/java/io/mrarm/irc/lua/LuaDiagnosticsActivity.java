package io.mrarm.irc.lua;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

import io.mrarm.irc.R;
import io.mrarm.irc.ThemedActivity;

/** Branch-only diagnostics: predefined probes, never arbitrary user scripts. */
public final class LuaDiagnosticsActivity extends ThemedActivity {
    private final List<Button> buttons = new ArrayList<>();
    private TextView output;
    private boolean bound;
    private boolean busy;
    private String scenario;
    private final Messenger responses = new Messenger(
            new Handler(Looper.getMainLooper(), this::handleResponse));
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            Message request = Message.obtain(null, LuaDiagnosticService.REQUEST_PROBE);
            Bundle data = new Bundle();
            data.putString(LuaDiagnosticService.EXTRA_SCENARIO, scenario);
            request.setData(data);
            request.replyTo = responses;
            try {
                new Messenger(binder).send(request);
            } catch (RemoteException error) {
                complete(getString(R.string.lua_diag_unavailable));
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            complete(getString(R.string.lua_diag_crashed));
        }

        @Override public void onBindingDied(ComponentName name) {
            complete(getString(R.string.lua_diag_crashed));
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.lua_diag_title);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding, padding, padding);
        scroll.addView(layout);
        TextView description = new TextView(this);
        description.setText(R.string.lua_diag_description);
        description.setTextSize(16);
        layout.addView(description);
        addButton(layout, R.string.lua_diag_arithmetic, "arithmetic");
        addButton(layout, R.string.lua_diag_denied, "denied");
        addButton(layout, R.string.lua_diag_loop, "loop");
        addButton(layout, R.string.lua_diag_memory, "memory");
        addButton(layout, R.string.lua_diag_crash, "crash");
        output = new TextView(this);
        output.setText(R.string.lua_diag_ready);
        output.setTextIsSelectable(true);
        output.setTextSize(15);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = padding;
        layout.addView(output, params);
        setContentView(scroll);
    }

    private void addButton(LinearLayout layout, int label, String mode) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> runScenario(mode));
        layout.addView(button);
        buttons.add(button);
    }

    private void runScenario(String mode) {
        if (busy) return;
        scenario = mode;
        busy = true;
        for (Button button : buttons) button.setEnabled(false);
        output.setText(R.string.lua_diag_running);
        try {
            bound = bindService(new Intent(this, LuaDiagnosticService.class),
                    connection, Context.BIND_AUTO_CREATE);
            if (!bound) complete(getString(R.string.lua_diag_unavailable));
        } catch (RuntimeException error) {
            complete(getString(R.string.lua_diag_unavailable));
        }
    }

    private boolean handleResponse(Message message) {
        if (message.what != LuaDiagnosticService.RESPONSE_PROBE) return true;
        Bundle data = message.getData();
        boolean isolated = data.getBoolean(LuaDiagnosticService.EXTRA_ISOLATED);
        int pid = data.getInt(LuaDiagnosticService.EXTRA_PID, -1);
        String result = data.getString(LuaDiagnosticService.EXTRA_RESULT, "");
        complete(getString(R.string.lua_diag_result, isolated, pid, result));
        return true;
    }

    private void complete(String text) {
        if (output != null) output.setText(text);
        releaseBinding();
        busy = false;
        for (Button button : buttons) button.setEnabled(true);
    }

    private void releaseBinding() {
        if (bound) {
            bound = false;
            unbindService(connection);
        }
    }

    @Override protected void onStop() {
        releaseBinding();
        busy = false;
        for (Button button : buttons) button.setEnabled(true);
        super.onStop();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
