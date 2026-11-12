package io.mrarm.irc.lua;

import android.net.Uri;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.IOException;
import java.util.List;

import io.mrarm.irc.R;
import io.mrarm.irc.ThemedActivity;
import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.config.ServerConfigManager;

/** User-selected .lua imports and per-script/per-server permissions. */
public final class LuaScriptsActivity extends ThemedActivity {
    // Register before the Activity reaches STARTED; Android restores pending
    // document selections across an Activity recreation.
    private final ActivityResultLauncher<String[]> luaPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), selected -> {
                if (selected != null) importScript(selected);
            });
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
                if (s.allowUserInfo)
                    state += "\n" + getString(R.string.lua_scripts_users_enabled);
                if (s.allowModeration)
                    state += "\n" + getString(R.string.lua_scripts_moderation_enabled);
                if (s.allowStorage)
                    state += "\n" + getString(R.string.lua_scripts_storage_enabled);
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
            LuaScriptStore.Output output = store.latestOutput(s.id);
            if (output != null)
                list.addView(button(R.string.lua_scripts_view_output,
                        () -> showOutput(output)));
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

    private void showOutput(LuaScriptStore.Output output) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(output.title)
                .setNegativeButton(android.R.string.ok, null);
        if (output.list) {
            // Host-generated list controls only; a tap copies the selected
            // value. Lua cannot create intents, activities or arbitrary views.
            String[] items = output.body.split("\n", -1);
            dialog.setItems(items, (d, index) -> {
                ClipboardManager clipboard = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText(
                            getString(R.string.lua_scripts_copy_label), items[index]));
                    Toast.makeText(this, R.string.lua_scripts_copied,
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            dialog.setMessage(output.body);
        }
        dialog.show();
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
        // A message and setMultiChoiceItems cannot be shown together reliably by
        // AlertDialog. Keep both the explanation and the optional send approval
        // visible in one scrollable view, with sending unchecked by default.
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, Math.round(8 * getResources().getDisplayMetrics().density),
                padding, Math.round(8 * getResources().getDisplayMetrics().density));
        content.addView(text(getString(R.string.lua_scripts_consent_body,
                server.name == null ? server.uuid.toString() : server.name), 16));
        CheckBox allowSend = new CheckBox(this);
        allowSend.setText(R.string.lua_scripts_send_permission);
        allowSend.setChecked(false);
        content.addView(allowSend);
        CheckBox allowUsers = new CheckBox(this);
        allowUsers.setText(R.string.lua_scripts_users_permission);
        allowUsers.setChecked(false);
        content.addView(allowUsers);
        CheckBox allowModeration = new CheckBox(this);
        allowModeration.setText(R.string.lua_scripts_moderation_permission);
        allowModeration.setChecked(false);
        content.addView(allowModeration);
        CheckBox allowStorage = new CheckBox(this);
        allowStorage.setText(R.string.lua_scripts_storage_permission);
        allowStorage.setChecked(false);
        content.addView(allowStorage);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);

        new AlertDialog.Builder(this)
                .setTitle(R.string.lua_scripts_consent_title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.lua_scripts_enable, (dialog, which) -> {
                    try {
                        store.enable(s.id, server.uuid.toString(), allowSend.isChecked(),
                                allowUsers.isChecked(), allowModeration.isChecked(),
                                allowStorage.isChecked());
                        LuaScriptDispatcher.get(this).reload();
                        render();
                    } catch (IOException error) {
                        failure(error);
                    }
                }).show();
    }

    private void openPicker() {
        // .lua has no consistently advertised MIME type across file providers.
        // Keep the original "*/*" filter; importFromUri verifies name/content.
        luaPicker.launch(new String[]{"*/*"});
    }

    private void importScript(Uri selected) {
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
