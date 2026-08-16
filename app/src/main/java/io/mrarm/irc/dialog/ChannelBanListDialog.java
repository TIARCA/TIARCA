package io.mrarm.irc.dialog;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.mrarm.chatlib.irc.CommandHandlerList;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.irc.BanListCommandHandler;

/** Sortable, multi-select channel ban list. */
public final class ChannelBanListDialog {

    private static final long CLEANUP_MIN_AGE_SECONDS = 6L * 60L * 60L;
    private static final long CLEANUP_MAX_AGE_SECONDS = 30L * 60L * 60L;

    private final Activity activity;
    private final ServerConnectionInfo connection;
    private final String channel;
    private final List<Row> rows = new ArrayList<>();
    private LinearLayout list;
    private int sortColumn;
    private boolean ascending = true;

    public ChannelBanListDialog(Activity activity, ServerConnectionInfo connection,
                                String channel) {
        this.activity = activity;
        this.connection = connection;
        this.channel = channel;
    }

    public void show() {
        if (!(connection.getApiInstance() instanceof IRCConnection))
            return;
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        CommandHandlerList handlers = irc.getServerConnectionData().getCommandHandlerList();
        BanListCommandHandler handler = handlers.getHandler(BanListCommandHandler.class);
        if (handler == null) {
            handler = new BanListCommandHandler();
            handlers.registerHandler(handler);
        }
        Toast.makeText(activity, R.string.ban_list_loading, Toast.LENGTH_SHORT).show();
        handler.request(channel, entries -> activity.runOnUiThread(() -> showList(entries)));
        irc.sendCommandRaw("MODE " + channel + " +b", null,
                error -> activity.runOnUiThread(() -> Toast.makeText(activity,
                        R.string.ban_list_failed, Toast.LENGTH_LONG).show()));
    }

    private void showList(List<BanListCommandHandler.Entry> entries) {
        rows.clear();
        for (BanListCommandHandler.Entry entry : entries)
            rows.add(new Row(entry));
        int pad = (int) (8 * activity.getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);
        LinearLayout headers = new LinearLayout(activity);
        headers.setOrientation(LinearLayout.HORIZONTAL);
        Button host = header(R.string.ban_list_host, 0);
        Button setter = header(R.string.ban_list_setter, 1);
        Button date = header(R.string.ban_list_date, 2);
        headers.addView(host, weighted(2));
        headers.addView(setter, weighted(1));
        headers.addView(date, weighted(1));
        root.addView(headers);
        list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,
                (int) (420 * activity.getResources().getDisplayMetrics().density)));
        LinearLayout actions = new LinearLayout(activity);
        Button selectAll = new Button(activity);
        selectAll.setText(R.string.ban_list_select_all);
        selectAll.setOnClickListener(v -> {
            boolean all = true;
            for (Row row : rows) all &= row.selected;
            for (Row row : rows) row.selected = !all;
            renderRows();
        });
        actions.addView(selectAll, weighted(1));
        Button cleanup = new Button(activity);
        cleanup.setText(R.string.ban_list_cleanup);
        actions.addView(cleanup, weighted(1));
        root.addView(actions);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.ban_list_title, channel))
                .setView(root)
                .setNegativeButton(R.string.action_close, null)
                .setPositiveButton(R.string.ban_list_remove_selected, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> confirmRemoval(dialog)));
        cleanup.setOnClickListener(v -> prepareCleanup(dialog));
        dialog.show();
        renderRows();
    }

    private Button header(int title, int column) {
        Button button = new Button(activity);
        button.setText(title);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setOnClickListener(v -> {
            if (sortColumn == column) ascending = !ascending;
            else { sortColumn = column; ascending = true; }
            sortRows();
            renderRows();
        });
        return button;
    }

    private void sortRows() {
        Comparator<Row> comparator;
        if (sortColumn == 1)
            comparator = (a, b) -> a.entry.setter.compareToIgnoreCase(b.entry.setter);
        else if (sortColumn == 2)
            comparator = (a, b) -> Long.compare(a.entry.timestamp, b.entry.timestamp);
        else
            comparator = (a, b) -> hostPart(a.entry.mask).compareToIgnoreCase(hostPart(b.entry.mask));
        if (!ascending)
            comparator = Collections.reverseOrder(comparator);
        Collections.sort(rows, comparator);
    }

    private void renderRows() {
        list.removeAllViews();
        if (rows.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText(R.string.ban_list_empty);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty);
            return;
        }
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (Row row : rows) {
            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);
            CheckBox mask = new CheckBox(activity);
            mask.setText(row.entry.mask);
            mask.setChecked(row.selected);
            mask.setOnCheckedChangeListener((button, checked) -> row.selected = checked);
            TextView setter = cell(row.entry.setter);
            TextView date = cell(row.entry.timestamp > 0
                    ? format.format(new Date(row.entry.timestamp * 1000L))
                    : activity.getString(R.string.ban_list_unknown));
            line.addView(mask, weighted(2));
            line.addView(setter, weighted(1));
            line.addView(date, weighted(1));
            list.addView(line);
        }
    }

    private void confirmRemoval(AlertDialog parent) {
        List<Row> selected = new ArrayList<>();
        List<String> masks = new ArrayList<>();
        for (Row row : rows) if (row.selected) {
            selected.add(row); masks.add(row.entry.mask);
        }
        if (selected.isEmpty()) {
            Toast.makeText(activity, R.string.ban_list_select_one, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.ban_list_confirm_title)
                .setMessage(android.text.TextUtils.join("\n", masks))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.ban_list_remove_selected, (dialog, which) -> {
                    removeRows(selected);
                }).show();
    }

    private void prepareCleanup(AlertDialog parent) {
        List<Row> candidates = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000L;
        for (Row row : rows) {
            row.selected = isCleanupCandidate(row.entry, now);
            if (row.selected)
                candidates.add(row);
        }
        renderRows();
        if (candidates.isEmpty()) {
            Toast.makeText(activity, R.string.ban_list_cleanup_none,
                    Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.ban_list_cleanup_confirm_title)
                .setMessage(activity.getResources().getQuantityString(
                        R.plurals.ban_list_cleanup_confirm_message, candidates.size(),
                        candidates.size()))
                .setNegativeButton(R.string.action_cancel, null)
                .setNeutralButton(R.string.ban_list_cleanup_review, null)
                .setPositiveButton(R.string.ban_list_cleanup_remove, (dialog, which) ->
                        removeRows(candidates))
                .show();
    }

    private void removeRows(List<Row> selected) {
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        for (Row row : selected)
            irc.sendCommandRaw("MODE " + channel + " -b " + row.entry.mask,
                    null, null);
        rows.removeAll(selected);
        renderRows();
        Toast.makeText(activity, R.string.operator_command_sent,
                Toast.LENGTH_SHORT).show();
    }

    static boolean isCleanupCandidate(BanListCommandHandler.Entry entry, long now) {
        if (entry == null || entry.mask == null || entry.timestamp <= 0)
            return false;
        long age = now - entry.timestamp;
        if (age < CLEANUP_MIN_AGE_SECONDS || age > CLEANUP_MAX_AGE_SECONDS)
            return false;

        String mask = entry.mask.trim();
        String lower = mask.toLowerCase(Locale.ROOT);
        if (lower.startsWith("j:") || lower.startsWith("r:"))
            return false;
        if (lower.startsWith("m:") || lower.startsWith("u:"))
            mask = mask.substring(2);
        else if (mask.indexOf(':') >= 0)
            return false;

        int bang = mask.indexOf('!');
        int at = mask.lastIndexOf('@');
        if (bang < 0 || at <= bang || at >= mask.length() - 1)
            return false;
        String ident = mask.substring(bang + 1, at);
        String host = mask.substring(at + 1);
        return hasSpecificPart(ident) || hasSpecificPart(host);
    }

    private static boolean hasSpecificPart(String value) {
        return value != null && !value.replace("*", "").replace("?", "").isEmpty();
    }

    private TextView cell(String value) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private static LinearLayout.LayoutParams weighted(int weight) {
        return new LinearLayout.LayoutParams(0, -2, weight);
    }

    private static String hostPart(String mask) {
        int at = mask.lastIndexOf('@');
        return (at >= 0 && at + 1 < mask.length() ? mask.substring(at + 1) : mask)
                .toLowerCase(Locale.ROOT);
    }

    private static class Row {
        final BanListCommandHandler.Entry entry;
        boolean selected;
        Row(BanListCommandHandler.Entry entry) { this.entry = entry; }
    }
}
