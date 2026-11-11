package io.mrarm.irc.lua;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.IOException;
import java.util.List;

import io.mrarm.irc.R;
import io.mrarm.irc.ThemedActivity;
import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.config.ServerConfigManager;

/** User-selected .lua imports and per-script/per-server permissions. */
public final class LuaScriptsActivity extends ThemedActivity {
    private static final int PICK_LUA = 1601;
    private LuaScriptStore store;
    private LinearLayout list;
    private int padding;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.lua_scripts_title);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        store = LuaScriptStore.get(this);
        padding = Math.round(16 * getResources().getDisplayMetrics().density);
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(padding, padding, padding, padding);
        scroll.addView(list);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private TextView text(String content, float size) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(size);
        view.setPadding(0, Math.round(5 * getResources().getDisplayMetrics().density),
                0, Math.round(5 * getResources().getDisplayMetrics().density));
        return view;
    }

    private Button button(int label, Runnable action) {
        Button result = new Button(this);
        result.setText(label);
        result.setOnClickListener(view -> action.run());
        return result;
    }

    private void render() {
        list.removeAllViews();
        list.addView(text(getString(R.string.lua_scripts_description), 15));
        list.addView(button(R.string.lua_scripts_import, this::openPicker));
        list.addView(button(R.string.lua_scripts_remove_all, () ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.lua_scripts_remove_all)
                        .setMessage(R.string.lua_scripts_remove_all_confirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.lua_scripts_remove_all, (dialog, which) -> {
                            try {
                                store.clearAll();
                                LuaScriptDispatcher.get(this).reload();
                                render();
                            } catch (IOException error) {
                                failure(error);
                            }
                        }).show()));
        list.addView(button(R.string.lua_scripts_refresh, this::render));
        List<LuaScriptStore.Script> scripts = store.list();
        if (scripts.isEmpty()) {
            list.addView(text(getString(R.string.lua_scripts_empty), 15));
            return;
        }
        for (LuaScriptStore.Script s : scripts) {
            TextView name = text(s.name, 18);
            list.addView(name);
            String state = s.enabled ? getString(R.string.lua_scripts_enabled) :
                    getString(R.string.lua_scripts_disabled);
            if (s.enabled) {
                state += "\n" + getString(R.string.lua_scripts_server_label,
                        resolveServerName(s.serverId));
                if (s.allowSend)
                    state += "\n" + getString(R.string.lua_scripts_sending_enabled);
            }
            if (s.error != null && !s.error.isEmpty())
                state += "\n" + getString(R.string.lua_scripts_last_error, s.error);
            String last = store.recentLog(s.id);
            if (last != null && !last.isEmpty())
                state += "\n" + getString(R.string.lua_scripts_last_log, last);
            list.addView(text(state, 14));
            list.addView(button(s.enabled ? R.string.lua_scripts_disable :
                    R.string.lua_scripts_enable,
                    () -> {
                        if (s.enabled) {
                            try {
                                store.disable(s.id);
                                LuaScriptDispatcher.get(this).reload();
                                render();
                            } catch (IOException error) {
                                failure(error);
                            }
                        } else {
                            chooseServer(s);
                        }
                    }));
            if (s.enabled) {
                list.addView(button(R.string.lua_scripts_reload, () -> {
                    LuaScriptDispatcher.get(this).reload();
                    Toast.makeText(this, R.string.lua_scripts_reloaded,
                            Toast.LENGTH_SHORT).show();
                    render();
                }));
            }
            list.addView(button(R.string.lua_scripts_delete, () ->
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.lua_scripts_delete)
                            .setMessage(s.name)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.lua_scripts_delete, (dialog, which) -> {
                                try {
                                    store.remove(s.id);
                                    LuaScriptDispatcher.get(this).reload();
                                    render();
                                } catch (IOException error) {
                                    failure(error);
                                }
                            }).show()));
            TextView separator = text("────────────────────────", 12);
            separator.setAlpha(0.5f);
            list.addView(separator);
        }
    }

    private String resolveServerName(String id) {
        for (ServerConfigData config : ServerConfigManager.getInstance(this).getServers())
            if (config.uuid != null && config.uuid.toString().equals(id))
                return config.name != null ? config.name : id;
        return id;
    }

    private void chooseServer(LuaScriptStore.Script s) {
        List<ServerConfigData> servers =
                ServerConfigManager.getInstance(this).getServers();
        if (servers.isEmpty()) {
            Toast.makeText(this, R.string.lua_scripts_no_servers, Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[servers.size()];
        for (int i = 0; i < servers.size(); i++) {
            ServerConfigData config = servers.get(i);
            labels[i] = config.name == null ? config.uuid.toString() : config.name;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.lua_scripts_choose_server)
                .setItems(labels, (dialog, which) -> askPermission(s, servers.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void askPermission(LuaScriptStore.Script s, ServerConfigData server) {
        final boolean[] send = {false};
        new AlertDialog.Builder(this)
                .setTitle(R.string.lua_scripts_consent_title)
                .setMessage(getString(R.string.lua_scripts_consent_body,
                        server.name == null ? server.uuid.toString() : server.name))
                .setMultiChoiceItems(new String[]{
                        getString(R.string.lua_scripts_send_permission)}, new boolean[]{false},
                        (dialog, position, checked) -> send[0] = checked)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.lua_scripts_enable, (dialog, which) -> {
                    try {
                        store.enable(s.id, server.uuid.toString(), send[0]);
                        LuaScriptDispatcher.get(this).reload();
                        render();
                    } catch (IOException error) {
                        failure(error);
                    }
                }).show();
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_LUA);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_LUA || resultCode != Activity.RESULT_OK ||
                data == null || data.getData() == null) return;
        final android.net.Uri selected = data.getData();
        // SAF providers may be remote/cloud backed. Do not block the UI thread
        // while reading the selected source document.
        new Thread(() -> {
            try {
                LuaScriptStore.Script imported = store.importFromUri(selected);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, getString(R.string.lua_scripts_imported, imported.name),
                            Toast.LENGTH_LONG).show();
                    render();
                });
            } catch (IOException error) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) failure(error);
                });
            }
        }, "tiarca-lua-import").start();
    }

    private void failure(Exception error) {
        new AlertDialog.Builder(this).setTitle(R.string.lua_scripts_error)
                .setMessage(error.getMessage())
                .setPositiveButton(android.R.string.ok, null).show();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
