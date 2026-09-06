package io.mrarm.irc.dialog;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
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

import io.mrarm.chatlib.dto.ModeList;
import io.mrarm.chatlib.irc.CommandHandlerList;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.irc.BanListCommandHandler;
import io.mrarm.irc.irc.ChannelModeSnapshotHandler;
import io.mrarm.irc.irc.ExceptionListCommandHandler;

/** Unified, sortable, multi-select channel Ban and Exception list dialog. */
public final class ChannelBanListDialog {

    private static final int TAB_BANS = 0;
    private static final int TAB_EXCEPTIONS = 1;

    private static final long CLEANUP_MIN_AGE_SECONDS = 48L * 60L * 60L;
    private static final long CLEANUP_MAX_AGE_SECONDS = 72L * 60L * 60L;
    private static final long IDENT_ONLY_CLEANUP_MIN_AGE_SECONDS = 7L * 24L * 60L * 60L;

    private final Activity activity;
    private final ServerConnectionInfo connection;
    private final String channel;

    private final List<Row> banRows = new ArrayList<>();
    private final List<Row> exceptionRows = new ArrayList<>();

    private int activeTab = TAB_BANS;
    private boolean exceptionsRequested = false;

    private LinearLayout list;
    private EditText search;
    private String searchQuery = "";
    private Button tabBan;
    private Button tabExceptions;
    private Button cleanupButton;

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

        BanListCommandHandler banHandler = handlers.getHandler(BanListCommandHandler.class);
        if (banHandler == null) {
            banHandler = new BanListCommandHandler();
            handlers.registerHandler(banHandler);
        }

        Toast.makeText(activity, R.string.ban_list_loading, Toast.LENGTH_SHORT).show();
        banHandler.request(channel, entries -> activity.runOnUiThread(() -> setBanEntries(entries)));
        irc.sendCommandRaw("MODE " + channel + " +b", null,
                error -> activity.runOnUiThread(() -> Toast.makeText(activity,
                        R.string.ban_list_failed, Toast.LENGTH_LONG).show()));

        if (supportsExceptions()) {
            requestExceptionsList(irc, handlers);
        }

        buildAndShowDialog(irc, handlers);
    }

    private boolean supportsExceptions() {
        if (!(connection.getApiInstance() instanceof IRCConnection))
            return false;
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        return irc.getServerConnectionData().getSupportList()
                .getSupportedListChannelModes().contains('e');
    }

    private void requestExceptionsList(IRCConnection irc, CommandHandlerList handlers) {
        if (exceptionsRequested)
            return;
        exceptionsRequested = true;
        ExceptionListCommandHandler exceptionHandler =
                handlers.getHandler(ExceptionListCommandHandler.class);
        if (exceptionHandler == null) {
            exceptionHandler = new ExceptionListCommandHandler();
            handlers.registerHandler(exceptionHandler);
        }
        exceptionHandler.request(channel, entries ->
                activity.runOnUiThread(() -> setExceptionEntries(entries)));
        irc.sendCommandRaw("MODE " + channel + " +e", null, null);
    }

    private void setBanEntries(List<BanListCommandHandler.Entry> entries) {
        banRows.clear();
        if (entries != null) {
            for (BanListCommandHandler.Entry entry : entries)
                banRows.add(new Row(entry.mask, entry.setter, entry.timestamp));
        }
        sortRows(banRows);
        renderRows();
    }

    private void setExceptionEntries(List<ExceptionListCommandHandler.Entry> entries) {
        exceptionRows.clear();
        if (entries != null) {
            for (ExceptionListCommandHandler.Entry entry : entries)
                exceptionRows.add(new Row(entry.mask, entry.setter, entry.timestamp));
        }
        sortRows(exceptionRows);
        renderRows();
    }

    private void buildAndShowDialog(IRCConnection irc, CommandHandlerList handlers) {
        int pad = (int) (8 * activity.getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        LinearLayout tabRow = new LinearLayout(activity);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabBan = new Button(activity);
        tabBan.setText(R.string.ban_list_tab_bans);
        tabExceptions = new Button(activity);
        tabExceptions.setText(R.string.ban_list_tab_exceptions);

        tabBan.setOnClickListener(v -> switchTab(TAB_BANS));
        tabExceptions.setOnClickListener(v -> switchTab(TAB_EXCEPTIONS));

        tabRow.addView(tabBan, weighted(1));
        tabRow.addView(tabExceptions, weighted(1));
        root.addView(tabRow);

        LinearLayout searchRow = new LinearLayout(activity);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        search = new EditText(activity);
        search.setHint(R.string.ban_list_search_hint);
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count,
                                                    int after) { }

            @Override public void onTextChanged(CharSequence text, int start, int before,
                                                int count) {
                searchQuery = text.toString();
                renderRows();
            }

            @Override public void afterTextChanged(Editable text) { }
        });

        Button clearSearch = new Button(activity);
        clearSearch.setText("×");
        clearSearch.setContentDescription(activity.getString(R.string.ban_list_search_clear));
        clearSearch.setOnClickListener(v -> search.setText(""));
        searchRow.addView(search, weighted(1));
        searchRow.addView(clearSearch);
        root.addView(searchRow);

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
            List<Row> visibleRows = getVisibleRows();
            boolean all = !visibleRows.isEmpty();
            for (Row row : visibleRows) all &= row.selected;
            for (Row row : visibleRows) row.selected = !all;
            renderRows();
        });
        actions.addView(selectAll, weighted(1));

        cleanupButton = new Button(activity);
        cleanupButton.setText(R.string.ban_list_cleanup);
        actions.addView(cleanupButton, weighted(1));
        root.addView(actions);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.ban_list_title, channel))
                .setView(root)
                .setNegativeButton(R.string.action_close, null)
                .setPositiveButton(R.string.ban_list_remove_selected, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> confirmRemoval(dialog)));
        cleanupButton.setOnClickListener(v -> prepareCleanup(dialog));

        ChannelModeSnapshotHandler snapshotHandler =
                handlers.getHandler(ChannelModeSnapshotHandler.class);
        ChannelModeSnapshotHandler.ModeListener modeListener =
                (conn, sender, params) -> {
                    if (params.size() < 2) return;
                    String targetChannel = params.get(0);
                    if (!targetChannel.equalsIgnoreCase(channel)) return;
                    String setterNick = sender != null ? sender.getNick() : "";
                    long now = System.currentTimeMillis() / 1000L;
                    String modeStr = params.get(1);
                    boolean adding = true;
                    int paramIdx = 2;
                    boolean changed = false;
                    for (int i = 0; i < modeStr.length(); i++) {
                        char c = modeStr.charAt(i);
                        if (c == '+') { adding = true; continue; }
                        if (c == '-') { adding = false; continue; }
                        if (c == 'b') {
                            if (paramIdx < params.size()) {
                                String mask = params.get(paramIdx++);
                                if (adding) {
                                    if (addEntryIfNotPresent(banRows, mask, setterNick, now))
                                        changed = true;
                                } else {
                                    if (removeEntryByMask(banRows, mask))
                                        changed = true;
                                }
                            }
                        } else if (c == 'e') {
                            if (paramIdx < params.size()) {
                                String mask = params.get(paramIdx++);
                                if (adding) {
                                    if (addEntryIfNotPresent(exceptionRows, mask, setterNick, now))
                                        changed = true;
                                } else {
                                    if (removeEntryByMask(exceptionRows, mask))
                                        changed = true;
                                }
                            }
                        } else {
                            ModeList listModes = conn.getSupportList().getSupportedListChannelModes();
                            ModeList nickPrefixModes = conn.getSupportList().getSupportedNickPrefixModes();
                            ModeList valueExactUnset = conn.getSupportList().getSupportedValueExactUnsetChannelModes();
                            ModeList valueModes = conn.getSupportList().getSupportedValueChannelModes();
                            if (listModes.contains(c) || nickPrefixModes.contains(c) || valueExactUnset.contains(c)
                                    || (adding && valueModes.contains(c))) {
                                paramIdx++;
                            }
                        }
                    }
                    if (changed) {
                        activity.runOnUiThread(() -> {
                            sortRows(activeTab == TAB_BANS ? banRows : exceptionRows);
                            renderRows();
                        });
                    }
                };

        if (snapshotHandler != null) {
            snapshotHandler.addModeListener(modeListener);
            dialog.setOnDismissListener(d -> snapshotHandler.removeModeListener(modeListener));
        }

        updateTabStyles();
        dialog.show();
        renderRows();
    }

    private static boolean addEntryIfNotPresent(List<Row> list, String mask, String setter, long now) {
        for (Row row : list) {
            if (row.mask.equalsIgnoreCase(mask))
                return false;
        }
        list.add(new Row(mask, setter, now));
        return true;
    }

    private static boolean removeEntryByMask(List<Row> list, String mask) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).mask.equalsIgnoreCase(mask)) {
                list.remove(i);
                return true;
            }
        }
        return false;
    }

    private void switchTab(int tab) {
        if (activeTab == tab)
            return;
        activeTab = tab;
        updateTabStyles();
        if (activeTab == TAB_EXCEPTIONS) {
            if (!supportsExceptions()) {
                Toast.makeText(activity, R.string.ban_list_exceptions_not_supported,
                        Toast.LENGTH_SHORT).show();
            } else if (!exceptionsRequested && connection.getApiInstance() instanceof IRCConnection) {
                IRCConnection irc = (IRCConnection) connection.getApiInstance();
                CommandHandlerList handlers = irc.getServerConnectionData().getCommandHandlerList();
                requestExceptionsList(irc, handlers);
            }
        }
        renderRows();
    }

    private void updateTabStyles() {
        if (activeTab == TAB_BANS) {
            tabBan.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tabExceptions.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            cleanupButton.setVisibility(View.VISIBLE);
        } else {
            tabExceptions.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tabBan.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            cleanupButton.setVisibility(View.GONE);
        }
    }

    private Button header(int title, int column) {
        Button button = new Button(activity);
        button.setText(title);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setOnClickListener(v -> {
            if (sortColumn == column) ascending = !ascending;
            else { sortColumn = column; ascending = true; }
            sortRows(activeTab == TAB_BANS ? banRows : exceptionRows);
            renderRows();
        });
        return button;
    }

    private void sortRows(List<Row> targetRows) {
        Comparator<Row> comparator;
        if (sortColumn == 1)
            comparator = (a, b) -> a.setter.compareToIgnoreCase(b.setter);
        else if (sortColumn == 2)
            comparator = (a, b) -> Long.compare(a.timestamp, b.timestamp);
        else
            comparator = (a, b) -> hostPart(a.mask).compareToIgnoreCase(hostPart(b.mask));
        if (!ascending)
            comparator = Collections.reverseOrder(comparator);
        Collections.sort(targetRows, comparator);
    }

    private void renderRows() {
        if (list == null) return;
        list.removeAllViews();
        List<Row> currentRows = activeTab == TAB_BANS ? banRows : exceptionRows;
        List<Row> visibleRows = getVisibleRows();

        if (visibleRows.isEmpty()) {
            TextView empty = new TextView(activity);
            if (activeTab == TAB_EXCEPTIONS && !supportsExceptions()) {
                empty.setText(R.string.ban_list_exceptions_not_supported);
            } else if (currentRows.isEmpty()) {
                empty.setText(R.string.ban_list_empty);
            } else {
                empty.setText(R.string.ban_list_no_results);
            }
            empty.setGravity(Gravity.CENTER);
            list.addView(empty);
            return;
        }

        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (Row row : visibleRows) {
            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);
            CheckBox mask = new CheckBox(activity);
            mask.setText(row.mask);
            mask.setChecked(row.selected);
            mask.setOnCheckedChangeListener((button, checked) -> row.selected = checked);
            TextView setter = cell(row.setter);
            TextView date = cell(row.timestamp > 0
                    ? format.format(new Date(row.timestamp * 1000L))
                    : activity.getString(R.string.ban_list_unknown));
            line.addView(mask, weighted(2));
            line.addView(setter, weighted(1));
            line.addView(date, weighted(1));
            list.addView(line);
        }
    }

    private void confirmRemoval(AlertDialog parent) {
        List<Row> currentRows = activeTab == TAB_BANS ? banRows : exceptionRows;
        List<Row> selected = new ArrayList<>();
        List<String> masks = new ArrayList<>();
        for (Row row : currentRows) if (row.selected) {
            selected.add(row); masks.add(row.mask);
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
                    removeRows(selected, activeTab);
                }).show();
    }

    private void prepareCleanup(AlertDialog parent) {
        if (activeTab != TAB_BANS)
            switchTab(TAB_BANS);

        List<Row> candidates = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000L;
        for (Row row : banRows) {
            row.selected = isCleanupCandidate(row.mask, row.timestamp, now);
            if (row.selected)
                candidates.add(row);
        }
        if (!searchQuery.isEmpty())
            search.setText("");
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
                        removeRows(candidates, TAB_BANS))
                .show();
    }

    private void removeRows(List<Row> selected, int tab) {
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        String modeFlag = (tab == TAB_EXCEPTIONS) ? " -e " : " -b ";
        for (Row row : selected)
            irc.sendCommandRaw("MODE " + channel + modeFlag + row.mask, null, null);

        List<Row> targetList = (tab == TAB_EXCEPTIONS) ? exceptionRows : banRows;
        targetList.removeAll(selected);
        renderRows();
        Toast.makeText(activity, R.string.operator_command_sent, Toast.LENGTH_SHORT).show();
    }

    public static boolean isCleanupCandidate(BanListCommandHandler.Entry entry, long now) {
        if (entry == null) return false;
        return isCleanupCandidate(entry.mask, entry.timestamp, now);
    }

    public static boolean isCleanupCandidate(String rawMask, long timestamp, long now) {
        return isLegacyCleanupCandidate(rawMask, timestamp, now)
                || isIdentOnly7DaysCleanupCandidate(rawMask, timestamp, now);
    }

    public static boolean isLegacyCleanupCandidate(String rawMask, long timestamp, long now) {
        if (rawMask == null || timestamp <= 0)
            return false;
        long age = now - timestamp;
        if (age < CLEANUP_MIN_AGE_SECONDS || age > CLEANUP_MAX_AGE_SECONDS)
            return false;

        String mask = rawMask.trim();
        String lower = mask.toLowerCase(Locale.ROOT);
        if (lower.startsWith("j:") || lower.startsWith("r:") || lower.startsWith("u:"))
            return false;
        if (lower.startsWith("m:"))
            mask = mask.substring(2);
        else if (mask.indexOf(':') >= 0)
            return false;

        int bang = mask.indexOf('!');
        int at = mask.lastIndexOf('@');
        if (bang < 0 || at <= bang || at >= mask.length() - 1)
            return false;
        String ident = mask.substring(bang + 1, at);
        String host = mask.substring(at + 1);
        if (hasSpecificPart(ident))
            return false;
        return hasSpecificPart(host);
    }

    public static boolean isIdentOnly7DaysCleanupCandidate(String rawMask, long timestamp, long now) {
        if (rawMask == null || timestamp <= 0)
            return false;
        long age = now - timestamp;
        if (age < IDENT_ONLY_CLEANUP_MIN_AGE_SECONDS)
            return false;

        String mask = rawMask.trim();
        if (mask.indexOf(':') >= 0)
            return false;

        if (!mask.startsWith("*!") || !mask.endsWith("@*"))
            return false;

        if (mask.length() <= 4)
            return false;

        String ident = mask.substring(2, mask.length() - 2);
        if (ident.isEmpty() || ident.contains("*") || ident.contains("?")
                || ident.contains("!") || ident.contains("@") || ident.contains(":"))
            return false;

        return true;
    }

    public static boolean matchesSearch(BanListCommandHandler.Entry entry, String query) {
        if (entry == null) return false;
        return matchesSearch(entry.mask, entry.setter, query);
    }

    public static boolean matchesSearch(ExceptionListCommandHandler.Entry entry, String query) {
        if (entry == null) return false;
        return matchesSearch(entry.mask, entry.setter, query);
    }

    public static boolean matchesSearch(String mask, String setter, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return normalizedQuery.isEmpty()
                || containsIgnoreCase(mask, normalizedQuery)
                || containsIgnoreCase(setter, normalizedQuery);
    }

    private List<Row> getVisibleRows() {
        List<Row> targetList = activeTab == TAB_BANS ? banRows : exceptionRows;
        List<Row> visibleRows = new ArrayList<>();
        for (Row row : targetList) {
            if (matchesSearch(row.mask, row.setter, searchQuery))
                visibleRows.add(row);
        }
        return visibleRows;
    }

    private static boolean containsIgnoreCase(String value, String lowerCaseQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerCaseQuery);
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
        final String mask;
        final String setter;
        final long timestamp;
        boolean selected;

        Row(String mask, String setter, long timestamp) {
            this.mask = mask;
            this.setter = setter;
            this.timestamp = timestamp;
        }

        Row(BanListCommandHandler.Entry entry) {
            this(entry.mask, entry.setter, entry.timestamp);
        }
    }
}
