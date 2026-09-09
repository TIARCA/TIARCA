package io.mrarm.irc.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.ImageViewCompat;

import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.dialog.NicknameContextMenu;
import io.mrarm.irc.dialog.UserBottomSheetDialog;
import io.mrarm.irc.irc.WhowasCommandHandler;

/** Interactive structured WHOWAS record displayed inline in the Server tab. */
public class WhowasRecordView extends LinearLayout {
    private final TextView title;
    private final Row nick;
    private final Row ident;
    private final Row host;
    private final Row realName;
    private final Row server;
    private final Row disconnected;
    private final Row serverInfo;

    public WhowasRecordView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        int p = dp(12);
        setPadding(p, dp(8), p, dp(8));
        title = new TextView(context);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextSize(16);
        addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        nick = addRow(R.string.user_nick, true);
        ident = addRow(R.string.whowas_ident, true);
        host = addRow(R.string.whowas_host, true);
        realName = addRow(R.string.whowas_real_name, false);
        server = addRow(R.string.whowas_server, true);
        disconnected = addRow(R.string.whowas_disconnected_at, false);
        serverInfo = addRow(R.string.whowas_server_info, false);
    }

    public void bind(ServerConnectionInfo connection, WhowasCommandHandler.Result result) {
        title.setText(getContext().getString(R.string.whowas_record_title, result.nick));
        bindRow(nick, result.nick);
        bindRow(ident, result.user);
        bindRow(host, result.host);
        bindRow(realName, result.realName);
        bindRow(server, result.server);
        String when = result.disconnectTime == null ? null :
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,
                        java.text.DateFormat.MEDIUM).format(result.disconnectTime);
        bindRow(disconnected, when);
        bindRow(serverInfo, result.serverInfo);

        nick.value.setOnLongClickListener(v -> {
            NicknameContextMenu.show(getContext(), connection, result.nick, null);
            return true;
        });
        ident.value.setOnLongClickListener(v -> {
            if (result.user != null)
                UserBottomSheetDialog.showHistoricalKickban(getContext(), connection, result.nick,
                        "*!" + result.user + "@*", R.string.operator_kickban_ident);
            return true;
        });
        host.value.setOnLongClickListener(v -> {
            if (result.host != null)
                UserBottomSheetDialog.showHistoricalKickban(getContext(), connection, result.nick,
                        "*!*@" + result.host, R.string.operator_kickban);
            return true;
        });
    }

    private Row addRow(int labelId, boolean copyable) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(getContext());
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setText(labelId);
        TextView value = new TextView(getContext());
        value.setTextIsSelectable(true);
        LayoutParams labelParams = new LayoutParams(dp(105), LayoutParams.WRAP_CONTENT);
        LayoutParams valueParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        row.addView(label, labelParams);
        row.addView(value, valueParams);
        ImageButton copy = null;
        if (copyable) {
            copy = new ImageButton(getContext());
            copy.setImageResource(R.drawable.ic_content_copy);
            ImageViewCompat.setImageTintList(copy, ColorStateList.valueOf(Color.GRAY));
            TypedValue selectable = new TypedValue();
            if (getContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless, selectable, true))
                copy.setBackgroundResource(selectable.resourceId);
            copy.setContentDescription(getContext().getString(R.string.action_copy));
            row.addView(copy, new LayoutParams(dp(32), dp(32)));
        }
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return new Row(row, label, value, copy);
    }

    private void bindRow(Row row, String value) {
        boolean visible = value != null && !value.trim().isEmpty();
        row.container.setVisibility(visible ? VISIBLE : GONE);
        row.value.setText(visible ? value : "");
        if (row.copy != null) {
            row.copy.setEnabled(visible);
            row.copy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null)
                    clipboard.setPrimaryClip(ClipData.newPlainText(row.label.getText(), value));
            });
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class Row {
        final View container;
        final TextView label;
        final TextView value;
        final ImageButton copy;
        Row(View container, TextView label, TextView value, ImageButton copy) {
            this.container = container;
            this.label = label;
            this.value = value;
            this.copy = copy;
        }
    }
}
