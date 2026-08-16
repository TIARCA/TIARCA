package io.mrarm.irc.dialog;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.text.style.BackgroundColorSpan;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.mrarm.chatlib.dto.MessageFilterOptions;
import io.mrarm.chatlib.dto.MessageId;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageList;
import io.mrarm.irc.R;
import io.mrarm.irc.chat.ChatFragment;

/** Searches the recent persisted message history of the active channel or query. */
public class ChatMessageSearchDialog {

    private final Activity activity;
    private final ChatFragment fragment;
    private final String channel;
    private final List<Result> allMessages = new ArrayList<>();
    private final List<Result> matches = new ArrayList<>();
    private EditText query;
    private TextView status;
    private TextView preview;
    private Button previous;
    private Button next;
    private int current = -1;
    private String restoreMessageId;

    public ChatMessageSearchDialog(Activity activity, ChatFragment fragment) {
        this.activity = activity;
        this.fragment = fragment;
        this.channel = fragment.getCurrentChannel();
    }

    public void show() {
        restoreMessageId = fragment.getCurrentVisibleMessageId();
        int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding / 2, padding, 0);

        query = new EditText(activity);
        query.setSingleLine(true);
        query.setHint(R.string.search_messages_hint);
        root.addView(query, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(activity);
        status.setText(R.string.search_messages_loading);
        status.setPadding(0, padding / 2, 0, padding / 2);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        preview = new TextView(activity);
        preview.setTypeface(Typeface.MONOSPACE);
        preview.setTextIsSelectable(true);
        root.addView(preview, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        previous = new Button(activity);
        previous.setText(R.string.search_previous);
        next = new Button(activity);
        next.setText(R.string.search_next);
        controls.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.search_messages_title)
                .setView(root)
                .setNegativeButton(R.string.action_close, null)
                .create();
        previous.setEnabled(false);
        next.setEnabled(false);
        previous.setOnClickListener(v -> move(-1));
        next.setOnClickListener(v -> move(1));
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        dialog.show();
        dialog.setOnDismissListener(ignored -> {
            if (restoreMessageId != null)
                fragment.setCurrentChannel(channel, restoreMessageId);
        });
        loadMessages();
    }

    private void loadMessages() {
        MessageFilterOptions options = new MessageFilterOptions();
        options.restrictToMessageTypes = Arrays.asList(MessageInfo.MessageType.NORMAL,
                MessageInfo.MessageType.NOTICE, MessageInfo.MessageType.ME);
        fragment.getConnectionInfo().getApiInstance().getMessageStorageApi()
                .getMessages(channel, 5000, options, null, this::onMessages,
                        error -> activity.runOnUiThread(() ->
                                status.setText(R.string.search_messages_failed)));
    }

    private void onMessages(MessageList list) {
        activity.runOnUiThread(() -> {
            allMessages.clear();
            int count = Math.min(list.getMessages().size(), list.getMessageIds().size());
            for (int i = 0; i < count; i++) {
                MessageInfo message = list.getMessages().get(i);
                if (message.getMessage() != null)
                    allMessages.add(new Result(list.getMessageIds().get(i), message));
            }
            filter();
            query.requestFocus();
        });
    }

    private void filter() {
        if (query == null)
            return;
        String value = query.getText().toString().trim().toLowerCase(Locale.ROOT);
        matches.clear();
        if (!value.isEmpty()) {
            for (Result result : allMessages) {
                String nick = result.message.getSender() != null &&
                        result.message.getSender().getNick() != null
                        ? result.message.getSender().getNick() : "";
                if (result.message.getMessage().toLowerCase(Locale.ROOT).contains(value) ||
                        nick.toLowerCase(Locale.ROOT).contains(value))
                    matches.add(result);
            }
        }
        int oldCurrent = current;
        current = matches.isEmpty() ? -1 : Math.min(Math.max(oldCurrent, 0), matches.size() - 1);
        updateResult(false);
    }

    private void move(int delta) {
        if (matches.isEmpty())
            return;
        current = (current + delta + matches.size()) % matches.size();
        updateResult(true);
    }

    private void updateResult(boolean jump) {
        boolean enabled = current >= 0;
        previous.setEnabled(enabled);
        next.setEnabled(enabled);
        if (!enabled) {
            status.setText(query.getText().length() == 0
                    ? activity.getString(R.string.search_messages_ready, allMessages.size())
                    : activity.getString(R.string.search_messages_no_results));
            preview.setText("");
            return;
        }
        Result result = matches.get(current);
        status.setText(activity.getString(R.string.search_messages_count,
                current + 1, matches.size()));
        String nick = result.message.getSender() != null && result.message.getSender().getNick() != null
                ? result.message.getSender().getNick() : "";
        String date = result.message.getDate() == null ? "" :
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(result.message.getDate());
        String text = date + "  " + nick + "\n" + result.message.getMessage();
        SpannableString highlighted = new SpannableString(text);
        String needle = query.getText().toString().trim().toLowerCase(Locale.ROOT);
        String searchable = text.toLowerCase(Locale.ROOT);
        int start = 0;
        while (!needle.isEmpty() && (start = searchable.indexOf(needle, start)) >= 0) {
            highlighted.setSpan(new StyleSpan(Typeface.BOLD), start, start + needle.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            highlighted.setSpan(new BackgroundColorSpan(Color.rgb(100, 60, 130)),
                    start, start + needle.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            start += needle.length();
        }
        preview.setText(highlighted);
        if (jump || enabled)
            fragment.setCurrentChannel(channel, result.id.toString());
    }

    private static class Result {
        final MessageId id;
        final MessageInfo message;
        Result(MessageId id, MessageInfo message) {
            this.id = id;
            this.message = message;
        }
    }
}
