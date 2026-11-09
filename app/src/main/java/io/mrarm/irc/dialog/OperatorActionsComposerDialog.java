package io.mrarm.irc.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import io.mrarm.chatlib.dto.ModeList;
import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.ServerSupportList;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.config.OperatorReasonSettings;
import io.mrarm.irc.util.ChannelOperatorUtils;

/**
 * Scrollable operator action editor. Each line in the preview is what OK sends to the
 * selected channel; generated commands can be edited, and a regenerate button is explicit.
 */
public final class OperatorActionsComposerDialog {
    private final Context context;
    private final ServerConnectionInfo connection;
    private final OperatorCommandComposer.State state = new OperatorCommandComposer.State();
    private final OperatorCommandComposer.Dialect dialect;
    private final boolean targetPresent;
    private final String realName;
    private final String countryCode;

    private EditText preview;
    private EditText mask;
    private EditText customReason;
    private TextView warning;
    private Button moreActing;
    private Button moreMatching;
    private CheckBox timedCheck;
    private CheckBox banCheck;
    private CheckBox unauthedCheck;
    private boolean updatingPreview;
    private boolean updatingMask;
    private boolean manuallyEdited;
    private final Map<Character, CheckBox> matchingChecks = new LinkedHashMap<>();

    public OperatorActionsComposerDialog(Context context, ServerConnectionInfo connection,
                                         String channel, String nick, String ident, String host,
                                         String realName, String account, String countryCode,
                                         boolean targetPresent) {
        this.context = context;
        this.connection = connection;
        this.realName = realName;
        this.countryCode = countryCode != null && countryCode.matches("(?i)[A-Z]{2}") &&
                !countryCode.equalsIgnoreCase("XX") ? countryCode.toUpperCase(java.util.Locale.ROOT) : null;
        this.targetPresent = targetPresent;
        state.channel = channel;
        state.nick = nick;
        state.maskNick = nick;
        state.ident = ident;
        state.host = host;
        state.account = account;
        if (this.countryCode != null) state.matchingValues.put('G', this.countryCode);
        if (host == null || host.isEmpty()) {
            state.useHost = false;
            state.useNick = true;
        }
        ServerSupportList support = ((IRCConnection) connection.getApiInstance())
                .getServerConnectionData().getSupportList();
        dialect = new OperatorCommandComposer.Dialect(support.getExtban(),
                support.getAccountExtban());
    }

    private int dp(int value) {
        return (int) (context.getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private TextView text(String value, int size) {
        TextView label = new TextView(context);
        label.setText(value);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        return label;
    }

    private LinearLayout vertical() {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        return group;
    }

    private LinearLayout horizontal() {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setGravity(Gravity.CENTER_VERTICAL);
        return group;
    }

    private void section(LinearLayout root, String title) {
        TextView header = text(title, 14);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, dp(5), 0, dp(1));
        root.addView(header);
    }

    private void line(LinearLayout root, View child) {
        root.addView(child, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private HorizontalScrollView scrollRow(LinearLayout root, LinearLayout row) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);
        line(root, scroll);
        return scroll;
    }

    private CheckBox checkbox(String title, boolean checked, boolean enabled,
                              java.util.function.Consumer<Boolean> change) {
        CheckBox box = new CheckBox(context);
        box.setText(title);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        box.setMinWidth(0);
        box.setMinimumWidth(0);
        box.setMinHeight(dp(32));
        box.setMinimumHeight(dp(32));
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setChecked(checked);
        box.setEnabled(enabled);
        box.setPadding(0, 0, dp(2), 0);
        box.setOnCheckedChangeListener((v, value) -> change.accept(value));
        return box;
    }

    private void buildMemberModes(LinearLayout root) {
        LinearLayout row = horizontal();
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        ServerSupportList support = irc.getServerConnectionData().getSupportList();
        ModeList modes = support.getSupportedNickPrefixModes();
        ModeList prefixes = support.getSupportedNickPrefixes();
        NickWithPrefix target = ChannelOperatorUtils.findMember(connection, state.channel, state.nick);
        NickWithPrefix own = ChannelOperatorUtils.findMember(connection, state.channel,
                irc.getServerConnectionData().getUserNick());
        int ownRank = Integer.MAX_VALUE;
        if (own != null && own.getNickPrefixes() != null) {
            for (int i = 0; i < modes.length(); i++) {
                if (i < prefixes.length() && own.getNickPrefixes().contains(prefixes.get(i)))
                    ownRank = Math.min(ownRank, i);
            }
        }
        for (int i = 0; i < modes.length(); i++) {
            char mode = modes.get(i);
            boolean active = target != null && target.getNickPrefixes() != null &&
                    i < prefixes.length() && target.getNickPrefixes().contains(prefixes.get(i));
            state.currentModes.put(mode, active);
            state.wantedModes.put(mode, active);
            String name;
            switch (mode) {
                case 'q': name = "owner"; break;
                case 'a': name = "admin"; break;
                case 'o': name = "op"; break;
                case 'h': name = "halfop"; break;
                case 'v': name = "voice"; break;
                default: name = "+" + mode;
            }
            // A rank may modify its own or lower ranks, subject to server policy.
            CheckBox box = checkbox(name, active, targetPresent && ownRank <= i, value -> {
                state.wantedModes.put(mode, value);
                updatePreview();
            });
            row.addView(box);
        }
        if (row.getChildCount() != 0) scrollRow(root, row);
    }

    private void buildActions(LinearLayout root) {
        LinearLayout kickRow = horizontal();
        kickRow.addView(checkbox(context.getString(R.string.operator_composer_kick), false, targetPresent, value -> {
            state.kick = value;
            updatePreview();
        }));
        List<String> reasons = new ArrayList<>(OperatorReasonSettings.getReasons(context));
        int customIndex = reasons.size();
        reasons.add(context.getString(R.string.operator_custom_reason));
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, reasons);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        kickRow.addView(spinner, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        line(root, kickRow);
        customReason = new EditText(context);
        customReason.setSingleLine(true);
        customReason.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        customReason.setHint(R.string.operator_reason_optional);
        customReason.setVisibility(View.GONE);
        line(root, customReason);
        customReason.addTextChangedListener(watcher(() -> {
            state.reason = customReason.getText().toString();
            updatePreview();
        }));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                  int position, long id) {
                customReason.setVisibility(position == customIndex ? View.VISIBLE : View.GONE);
                state.reason = position == customIndex ? customReason.getText().toString()
                        : reasons.get(position);
                updatePreview();
            }
        });

        LinearLayout banRow = horizontal();
        banCheck = checkbox(context.getString(R.string.operator_composer_ban), false, true, value -> {
            state.ban = value;
            if (!value && timedCheck != null && timedCheck.isChecked())
                timedCheck.setChecked(false);
            updatePreview();
        });
        banRow.addView(banCheck);
        timedCheck = checkbox(context.getString(R.string.operator_composer_timed), false, dialect.supportsTimed(), value -> {
            state.timed = value;
            if (value && !banCheck.isChecked()) banCheck.setChecked(true);
            updatePreview();
        });
        banRow.addView(timedCheck);
        EditText duration = new EditText(context);
        duration.setText("3h");
        duration.setSingleLine(true);
        duration.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        duration.setEms(4);
        duration.setHint("3h");
        duration.setEnabled(false);
        timedCheck.setOnCheckedChangeListener((v, checked) -> {
            state.timed = checked;
            if (checked && !banCheck.isChecked()) banCheck.setChecked(true);
            duration.setEnabled(checked);
            updatePreview();
        });
        duration.addTextChangedListener(watcher(() -> {
            state.duration = duration.getText().toString().trim();
            updatePreview();
        }));
        banRow.addView(duration);
        Button others = new Button(context);
        others.setText(R.string.operator_composer_more_actions);
        others.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        others.setOnClickListener(v -> {
            String[] options = {context.getString(R.string.operator_composer_remove_ban)};
            new AlertDialog.Builder(context).setTitle(R.string.operator_composer_more_actions)
                    .setMultiChoiceItems(options, new boolean[]{state.unban},
                            (dialog, index, checked) -> {
                                state.unban = checked;
                                updatePreview();
                            })
                    .setPositiveButton(R.string.action_ok, null).show();
        });
        banRow.addView(others);
        scrollRow(root, banRow);
    }

    private void toggleMatching(char code, boolean checked) {
        if (!checked) {
            state.matching.remove(code);
            updatePreview();
            return;
        }
        if (code == 'R' && TextUtils.isEmpty(state.account)) return;
        if (code != 'R' && code != 'U' &&
                !state.matchingValues.containsKey(code)) {
            promptValue("Valore " + code + ":", "", value -> {
                state.matchingValues.put(code, value);
                state.matching.add(code);
                updatePreview();
            });
            return;
        }
        state.matching.add(code);
        updatePreview();
    }

    private void promptValue(String title, String current,
                             java.util.function.Consumer<String> onAccepted) {
        EditText editor = new EditText(context);
        editor.setSingleLine(true);
        editor.setText(current == null ? "" : current);
        LinearLayout wrapper = vertical();
        wrapper.setPadding(dp(18), 0, dp(18), 0);
        wrapper.addView(editor);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title).setView(wrapper)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = editor.getText().toString().trim();
                    if (value.isEmpty() || value.matches(".*\\s.*")) {
                        editor.setError(context.getString(R.string.operator_invalid_mask));
                        return;
                    }
                    onAccepted.accept(value);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void showExtras(boolean acting) {
        char[] codes = acting ? new char[]{'A', 'c', 'C', 'S', 'T', 'Q'} :
                new char[]{'r', 'j', 's', 'z', 'B', 'o', 'O', 'n', 'w', 'a', 'b'};
        String[] descriptions = acting ?
                new String[]{"A: Blocca INVITE", "c: Blocca colori", "C: Blocca CTCP",
                        "S: Rimuovi colori", "T: Blocca NOTICE", "Q: Blocca KICK"} :
                new String[]{"r: Realname", "j: Altro canale", "s: Server", "z: Certificato",
                        "B: Bot", "o: IRCop", "O: Tipo IRCop", "n: Classe",
                        "w: Gateway", "a: Realmask", "b: Ban condivisi"};
        List<Character> supported = new ArrayList<>();
        List<String> options = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            if (acting ? dialect.supportsActing(codes[i]) : dialect.supportsMatching(codes[i])) {
                supported.add(codes[i]);
                options.add((acting ? state.acting : state.matching).contains(codes[i])
                        ? "☑ " + descriptions[i] : "☐ " + descriptions[i]);
            }
        }
        if (options.isEmpty()) {
            Toast.makeText(context, R.string.operator_composer_no_extras,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(context).setTitle(acting ? R.string.operator_composer_other_acting : R.string.operator_composer_other_matching)
                .setItems(options.toArray(new String[0]), (d, index) -> {
                    char code = supported.get(index);
                    if (acting) {
                        if (!state.acting.add(code)) state.acting.remove(code);
                        updatePreview();
                    } else {
                        toggleMatching(code, !state.matching.contains(code));
                    }
                    updateExtraLabels();
                })
                .setNegativeButton(R.string.action_cancel, null).show();
    }

    private void updateExtraLabels() {
        if (moreActing != null) moreActing.setText(context.getString(R.string.operator_composer_others_count,
                Math.max(0, state.acting.size() -
                        (state.acting.contains('m') ? 1 : 0) -
                        (state.acting.contains('u') ? 1 : 0) -
                        (state.acting.contains('N') ? 1 : 0))));
        if (moreMatching != null) moreMatching.setText(context.getString(R.string.operator_composer_others_count,
                Math.max(0, state.matching.size() -
                        (state.matching.contains('R') ? 1 : 0) -
                        (state.matching.contains('U') ? 1 : 0) -
                        (state.matching.contains('G') ? 1 : 0))));
    }

    private void buildExtbans(LinearLayout root) {
        if (!dialect.hasExtbans()) {
            TextView status = text(context.getString(R.string.operator_composer_missing_extban), 11);
            line(root, status);
        }
        // The section label and its "Other" selector occupy their own compact line.
        // Keeping all three checkboxes on the next line avoids horizontal clipping.
        moreActing = compactExtrasButton();
        moreActing.setOnClickListener(v -> showExtras(true));
        extbanSubheading(root, R.string.operator_composer_acting, moreActing);

        LinearLayout acts = horizontal();
        addActing(acts, 'm', context.getString(R.string.operator_composer_mute));
        addActing(acts, 'u', context.getString(R.string.operator_composer_auditorium));
        addActing(acts, 'N', context.getString(R.string.operator_composer_nonick));
        line(root, acts);

        moreMatching = compactExtrasButton();
        moreMatching.setOnClickListener(v -> showExtras(false));
        extbanSubheading(root, R.string.operator_composer_matching, moreMatching);

        LinearLayout matches = horizontal();
        addMatching(matches, 'R', context.getString(R.string.operator_composer_account));
        unauthedCheck = addMatching(matches, 'U', context.getString(R.string.operator_composer_unauth));
        addMatching(matches, 'G', context.getString(R.string.operator_composer_country));
        line(root, matches);
        updateExtraLabels();
    }

    private Button compactExtrasButton() {
        Button button = new Button(context);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        button.setAllCaps(false);
        button.setMinimumHeight(dp(30));
        button.setMinHeight(dp(30));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(6), 0, dp(6), 0);
        return button;
    }

    private void extbanSubheading(LinearLayout root, int labelId, Button extras) {
        LinearLayout row = horizontal();
        TextView label = text(context.getString(labelId), 11);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(extras, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)));
        line(root, row);
    }

    private void addActing(LinearLayout row, char code, String label) {
        CheckBox box = checkbox(label, false, dialect.supportsActing(code), value -> {
            if (value) state.acting.add(code);
            else state.acting.remove(code);
            updatePreview();
        });
        box.setMaxLines(2);
        // Auditorium needs more width than Mute, especially with enlarged fonts.
        row.addView(box, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, code == 'u' ? 1.35f : 0.90f));
    }

    private CheckBox addMatching(LinearLayout row, char code, String label) {
        final CheckBox[] holder = new CheckBox[1];
        CheckBox box = checkbox(label, false, dialect.supportsMatching(code) &&
                (code != 'R' || !TextUtils.isEmpty(state.account)), value -> {
            toggleMatching(code, value);
            // A value prompt can be cancelled: never leave a checked UI without a matching rule.
            if (value && !state.matching.contains(code)) {
                holder[0].post(() -> holder[0].setChecked(state.matching.contains(code)));
            }
        });
        holder[0] = box;
        matchingChecks.put(code, box);
        box.setMaxLines(2);
        // The full "Non autenticati" label must remain readable, not clipped.
        row.addView(box, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, code == 'U' ? 1.55f : 0.85f));
        return box;
    }

    private void refreshMask() {
        if (mask == null || updatingMask) return;
        state.manualMask = null;
        updatingMask = true;
        mask.setText(state.hostMask());
        updatingMask = false;
        updatePreview();
    }

    private void buildMask(LinearLayout root) {
        LinearLayout boxes = horizontal();
        boxes.addView(checkbox(context.getString(R.string.operator_composer_nickname), state.useNick, true, value -> {
            state.useNick = value;
            refreshMask();
        }));
        boxes.addView(checkbox(context.getString(R.string.operator_composer_ident), state.useIdent, true, value -> {
            state.useIdent = value;
            refreshMask();
        }));
        boxes.addView(checkbox(context.getString(R.string.operator_composer_host), state.useHost, true, value -> {
            state.useHost = value;
            refreshMask();
        }));
        scrollRow(root, boxes);
        mask = new EditText(context);
        mask.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mask.setSingleLine(true);
        mask.setHint(R.string.operator_mask);
        mask.setText(state.hostMask());
        line(root, mask);
        mask.addTextChangedListener(watcher(() -> {
            if (!updatingMask) {
                state.manualMask = mask.getText().toString();
                updatePreview();
            }
        }));
    }

    private void updatePreview() {
        // Keep prompted matching values and checkboxes in sync even when the prompt was cancelled.
        for (Map.Entry<Character, CheckBox> entry : matchingChecks.entrySet()) {
            boolean selected = state.matching.contains(entry.getKey());
            if (entry.getValue().isChecked() != selected) entry.getValue().setChecked(selected);
        }
        if (preview == null) return;
        OperatorCommandComposer.Result result = OperatorCommandComposer.build(state, dialect);
        if (manuallyEdited) {
            warning.setText(R.string.operator_composer_modified);
        } else {
            updatingPreview = true;
            preview.setText(result.preview());
            updatingPreview = false;
            warning.setText(result.error == null ? "" : result.error);
        }
        if (unauthedCheck != null && dialect.isUnrealStyle()) {
            // ~account:0 cannot be intersected with the selected host mask.
            unauthedCheck.setEnabled("*!*@*".equals(state.hostMask()));
        }
    }

    private static TextWatcher watcher(Runnable action) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                action.run();
            }
            @Override public void afterTextChanged(Editable s) { }
        };
    }

    public void show() {
        LinearLayout content = vertical();
        content.setPadding(dp(12), dp(2), dp(12), dp(4));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setFocusableInTouchMode(true);
        scroll.setClipToPadding(false);
        scroll.addView(content);

        section(content, context.getString(R.string.operator_composer_user_modes, state.channel));
        EditText identity = new EditText(context);
        identity.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        identity.setSingleLine(true);
        identity.setText(state.nick + "!" + (state.ident == null ? "*" : state.ident) +
                "@" + (state.host == null ? "*" : state.host));
        line(content, identity);
        identity.addTextChangedListener(watcher(() -> {
            String value = identity.getText().toString();
            int bang = value.indexOf('!');
            int at = value.indexOf('@', bang + 1);
            if (bang > 0 && at > bang && at < value.length() - 1) {
                state.maskNick = value.substring(0, bang);
                state.ident = value.substring(bang + 1, at);
                state.host = value.substring(at + 1);
                refreshMask();
            }
        }));
        TextView details = text(context.getString(R.string.operator_composer_details,
                realName == null ? context.getString(R.string.operator_composer_unavailable) : realName,
                state.account == null ? context.getString(R.string.operator_composer_unavailable) : state.account), 12);
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        line(content, details);
        buildMemberModes(content);

        section(content, context.getString(R.string.operator_composer_actions));
        buildActions(content);

        section(content, context.getString(R.string.operator_composer_extban));
        buildExtbans(content);

        section(content, context.getString(R.string.operator_composer_mask_match));
        buildMask(content);

        section(content, context.getString(R.string.operator_composer_preview));
        preview = new EditText(context);
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        preview.setTypeface(android.graphics.Typeface.MONOSPACE);
        preview.setMinLines(4);
        preview.setMaxLines(8);
        preview.setGravity(Gravity.TOP | Gravity.START);
        preview.setHorizontallyScrolling(false);
        line(content, preview);
        warning = text("", 11);
        line(content, warning);
        preview.addTextChangedListener(watcher(() -> {
            if (!updatingPreview) {
                manuallyEdited = true;
                warning.setText(R.string.operator_composer_manual);
            }
        }));
        Button regenerate = new Button(context);
        regenerate.setText(R.string.operator_composer_regenerate);
        regenerate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        regenerate.setMinHeight(dp(34));
        regenerate.setMinimumHeight(dp(34));
        regenerate.setPadding(dp(6), 0, dp(6), 0);
        regenerate.setOnClickListener(v -> {
            manuallyEdited = false;
            updatePreview();
        });
        line(content, regenerate);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.operator_actions_for, state.channel))
                .setView(scroll)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                    if (!UserBottomSheetDialog.hasOperatorPrivileges(connection, state.channel)) {
                        Toast.makeText(context, R.string.operator_not_authorised, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!manuallyEdited) {
                        OperatorCommandComposer.Result generated =
                                OperatorCommandComposer.build(state, dialect);
                        if (generated.error != null) {
                            warning.setText(generated.error);
                            return;
                        }
                    }
                    OperatorCommandComposer.Result parsed =
                            OperatorCommandComposer.parsePreview(preview.getText().toString(),
                                    state.channel);
                    if (parsed.error != null) {
                        preview.setError(parsed.error);
                        warning.setText(parsed.error);
                        return;
                    }
                    IRCConnection irc = (IRCConnection) connection.getApiInstance();
                    for (String command : parsed.commands)
                        irc.sendCommandRaw(command, null, null);
                    Toast.makeText(context, R.string.operator_command_sent, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }));
        Window window = dialog.getWindow();
        if (window != null) {
            // Reflow the dialog above the IME; without ADJUST_RESIZE the lower fields
            // disappear behind the keyboard on several Android versions.
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(context.getResources().getDisplayMetrics().widthPixels - dp(16),
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        // Do not focus an EditText on open: opening the keyboard would immediately
        // shrink the usable area before the operator has chosen an action.
        scroll.requestFocus();
        updatePreview();
    }
}
