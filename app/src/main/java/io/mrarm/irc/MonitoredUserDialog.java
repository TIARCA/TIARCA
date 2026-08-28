package io.mrarm.irc;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

import io.mrarm.chatlib.irc.IRCCaseMapping;
import io.mrarm.chatlib.irc.ServerConnectionApi;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.irc.MonitoredUsersManager;

/** Shared add/edit entry point, also used by nickname context actions. */
public final class MonitoredUserDialog {
    private MonitoredUserDialog() { }

    public static void show(Context context, ServerConnectionInfo connection,
                            ServerConfigData.MonitoredUser existing, Runnable onChanged) {
        show(context, connection, existing, null, onChanged);
    }

    public static void show(Context context, ServerConnectionInfo connection,
                            ServerConfigData.MonitoredUser existing, String initialNickname,
                            Runnable onChanged) {
        ServerConnectionApi api = connection.getApiInstance() instanceof ServerConnectionApi
                ? (ServerConnectionApi) connection.getApiInstance() : null;
        if (api == null) {
            Toast.makeText(context, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        ServerConnectionData data = api.getServerConnectionData();
        MonitoredUsersManager manager = connection.getMonitoredUsersManager();
        ArrayList<AliasDraft> aliases = new ArrayList<>();
        ArrayList<String> initialAliases = new ArrayList<>();
        if (existing != null) {
            for (ServerConfigData.MonitoredAlias alias : manager.getAliases(existing)) {
                aliases.add(new AliasDraft(alias.nick, alias.origin));
                initialAliases.add(alias.nick);
            }
        }

        LinearLayout content = new LinearLayout(context);
        int padding = (int) (24 * context.getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView primaryLabel = new TextView(context);
        primaryLabel.setText(R.string.monitor_primary_nickname);
        content.addView(primaryLabel);
        EditText primary = new EditText(context);
        primary.setHint(R.string.monitor_user_nickname);
        primary.setSingleLine(true);
        primary.setInputType(InputType.TYPE_CLASS_TEXT);
        String initialValue = resolveInitialNickname(existing, initialNickname);
        if (existing != null) {
            primary.setText(initialValue);
            primary.setEnabled(false);
        } else if (!initialValue.isEmpty()) {
            primary.setText(initialValue);
        }
        content.addView(primary);

        CheckBox presence = new CheckBox(context);
        presence.setText(R.string.monitor_user_presence);
        presence.setChecked(true);
        presence.setEnabled(false);
        CheckBox notifyOnline = new CheckBox(context);
        notifyOnline.setText(R.string.monitor_user_notify_online);
        notifyOnline.setChecked(existing != null && existing.notifyOnline);
        CheckBox notifyOffline = new CheckBox(context);
        notifyOffline.setText(R.string.monitor_user_notify_offline);
        notifyOffline.setChecked(existing != null && existing.notifyOffline);
        content.addView(presence);
        content.addView(notifyOnline);
        content.addView(notifyOffline);

        TextView aliasesLabel = new TextView(context);
        aliasesLabel.setText(R.string.monitor_aliases);
        content.addView(aliasesLabel);
        LinearLayout aliasList = new LinearLayout(context);
        aliasList.setOrientation(LinearLayout.VERTICAL);
        content.addView(aliasList);
        Button addAlias = new Button(context);
        addAlias.setText(R.string.action_add_monitor_alias);
        content.addView(addAlias);

        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            aliasList.removeAllViews();
            for (AliasDraft alias : new ArrayList<>(aliases)) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                TextView text = new TextView(context);
                String label = alias.nick;
                if (ServerConfigData.MonitoredAlias.ORIGIN_OBSERVED_NICK_CHANGE.equals(alias.origin))
                    label += " · " + context.getString(R.string.monitor_alias_observed);
                text.setText(label);
                text.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.addView(text, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                Button remove = new Button(context);
                remove.setText(R.string.action_remove_alias);
                remove.setEnabled(aliases.size() > 1 || existing == null);
                remove.setOnClickListener(v -> {
                    boolean wasPrimary = sameNick(data, alias.nick,
                            primary.getText().toString());
                    aliases.remove(alias);
                    if (wasPrimary && !aliases.isEmpty()) primary.setText(aliases.get(0).nick);
                    render[0].run();
                });
                row.addView(remove);
                aliasList.addView(row);
            }
        };
        render[0].run();

        addAlias.setOnClickListener(v -> showAddAliasDialog(context, data, manager, existing,
                primary, aliases, render[0]));

        String titleNick = initialValue;
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.monitor_user_title, titleNick))
                .setView(scroll)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, null);
        if (existing != null)
            builder.setNeutralButton(R.string.action_remove_monitored_user, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String target = primary.getText().toString().trim();
                if (target.isEmpty()) {
                    primary.setError(context.getString(R.string.monitor_user_nickname));
                    return;
                }
                ArrayList<String> aliasNames = new ArrayList<>();
                aliasNames.add(target);
                for (AliasDraft alias : aliases)
                    if (!contains(data, aliasNames, alias.nick)) aliasNames.add(alias.nick);
                if (existing != null) {
                    for (ServerConfigData.MonitoredAlias current : manager.getAliases(existing)) {
                        boolean addedWhileOpen = !contains(data, initialAliases, current.nick);
                        if (addedWhileOpen && !contains(data, aliasNames, current.nick))
                            aliasNames.add(current.nick);
                    }
                }
                try {
                    ServerConfigData.MonitoredUser user = existing;
                    if (user == null)
                        user = manager.addMonitoredUser(data, target, notifyOnline.isChecked(),
                                notifyOffline.isChecked());
                    manager.replaceAliases(data, user, target, aliasNames);
                    manager.updateNotificationPreferences(data, user.nick,
                            notifyOnline.isChecked(), notifyOffline.isChecked());
                } catch (MonitoredUsersManager.AliasConflictException conflict) {
                    Toast.makeText(context, context.getString(R.string.monitor_alias_conflict,
                            conflict.getExistingGroup().nick), Toast.LENGTH_LONG).show();
                    return;
                }
                onChanged.run();
                dialog.dismiss();
            });
            if (existing != null) {
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    manager.removeMonitoredUser(data, existing.nick);
                    onChanged.run();
                    dialog.dismiss();
                });
            }
        });
        dialog.show();
    }

    static String resolveInitialNickname(ServerConfigData.MonitoredUser existing,
                                         String contextualNickname) {
        if (existing != null && existing.nick != null)
            return existing.nick;
        return contextualNickname == null ? "" : contextualNickname.trim();
    }

    private static void showAddAliasDialog(Context context, ServerConnectionData data,
                                           MonitoredUsersManager manager,
                                           ServerConfigData.MonitoredUser existing,
                                           EditText primary,
                                           List<AliasDraft> aliases, Runnable onAdded) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.monitor_user_nickname);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.action_add_monitor_alias)
                .setView(input)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_add_alias_confirm, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String nick = input.getText().toString().trim();
                    if (nick.isEmpty()) {
                        input.setError(context.getString(R.string.monitor_user_nickname));
                        return;
                    }
                    if (sameNick(data, primary.getText().toString().trim(), nick)) {
                        input.setError(context.getString(R.string.monitor_alias_duplicate));
                        return;
                    }
                    for (AliasDraft alias : aliases) {
                        if (sameNick(data, alias.nick, nick)) {
                            input.setError(context.getString(R.string.monitor_alias_duplicate));
                            return;
                        }
                    }
                    ServerConfigData.MonitoredUser owner = manager.getMonitoredUser(data, nick);
                    if (owner != null && owner != existing) {
                        input.setError(context.getString(R.string.monitor_alias_conflict, owner.nick));
                        return;
                    }
                    aliases.add(new AliasDraft(nick, ServerConfigData.MonitoredAlias.ORIGIN_MANUAL));
                    onAdded.run();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private static boolean contains(ServerConnectionData data, List<String> aliases, String nick) {
        for (String alias : aliases) if (sameNick(data, alias, nick)) return true;
        return false;
    }

    private static boolean sameNick(ServerConnectionData data, String first, String second) {
        IRCCaseMapping mapping = data == null ? IRCCaseMapping.RFC1459 :
                data.getSupportList().getCaseMapping();
        return first != null && second != null && mapping.equals(first, second);
    }

    private static final class AliasDraft {
        final String nick;
        final String origin;

        AliasDraft(String nick, String origin) {
            this.nick = nick;
            this.origin = origin;
        }
    }
}
