package io.mrarm.irc.dialog;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.mrarm.chatlib.ChatApi;
import io.mrarm.chatlib.dto.WhoisInfo;
import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.chatlib.dto.ModeList;
import io.mrarm.chatlib.NoSuchChannelException;
import io.mrarm.chatlib.irc.ChannelData;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.irc.MainActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.util.AdvancedDividerItemDecoration;
import io.mrarm.irc.irc.WhowasCommandHandler;
import io.mrarm.irc.config.SharingSettings;
import io.mrarm.irc.config.OperatorReasonSettings;
import io.mrarm.irc.util.SimosnapAvatarLoader;
import io.mrarm.irc.util.SimosnapAvatarManager;

public class UserBottomSheetDialog {

    private Context mContext;
    private StatusBarColorBottomSheetDialog mDialog;
    private RecyclerView mRecyclerView;
    private ItemAdapter mAdapter;

    private ServerConnectionInfo mConnection;
    private String mNick;
    private String mUser;
    private String mHost;
    private String mRealName;
    private String mAccount;
    private String mSourceChannel;
    private boolean mSourceChannelExplicit;
    private boolean mHistoricalData;
    private boolean mTargetPresent;
    private List<String> mPendingChannelChoices;
    private boolean mAway;
    private List<Pair<String, String>> mEntries = new ArrayList<>();

    private HeaderHelper mHeader;

    public UserBottomSheetDialog(Context context) {
        mContext = context;
    }

    public void setConnection(ServerConnectionInfo connection) {
        mConnection = connection;
    }

    /**
     * Sets the chat channel from which this WHOIS panel was opened. Operator actions are
     * deliberately unavailable for server-status and private-message panels.
     */
    public void setSourceChannel(String channel) {
        mSourceChannel = isChannelName(channel) ? channel : null;
        mSourceChannelExplicit = mSourceChannel != null;
        refreshChannelState();
    }

    public void requestData(String nick, ChatApi connection) {
        mHost = null;
        mAccount = null;
        setUser(nick, null, null, false);
        connection.sendWhois(nick, (WhoisInfo info) -> {
            if (mRecyclerView != null)
                mRecyclerView.post(() -> setData(info));
            else
                setData(info);
        }, error -> requestWhowas(nick));
    }

    public void setData(WhoisInfo info) {
        mEntries.clear();
        mHistoricalData = false;
        mHost = info.getHost();
        mAccount = info.getLoggedInAsAccount();
        SimosnapAvatarManager.rememberAccount(mConnection, info.getNick(), mAccount);
        setUser(info.getNick(), info.getUser(), info.getRealName(), (info.getAwayMessage() != null));
        if (info.getAwayMessage() != null)
            addEntry(R.string.user_away, info.getAwayMessage());
        addEntry(R.string.user_hostname, info.getHost());
        if (info.getServer() != null)
            addEntry(R.string.user_server, mContext.getString(R.string.user_server_format, info.getServer(), info.getServerInfo()));
        if (info.getChannels() != null) {
            StringBuilder b = new StringBuilder();
            for (WhoisInfo.ChannelWithNickPrefixes channel : info.getChannels()) {
                if (b.length() > 0)
                    b.append(mContext.getString(R.string.text_comma));
                if (channel.getPrefixes() != null)
                    b.append(channel.getPrefixes());
                b.append(channel.getChannel());
            }
            addEntry(R.string.user_channels, b.toString());
        }
        if (info.getIdleSeconds() > 0)
            addEntry(R.string.user_idle, formatTime(info.getIdleSeconds()));
        if (info.getLoggedInAsAccount() != null)
            addEntry(R.string.user_account, info.getLoggedInAsAccount());
        if (info.isOperator()) {
            addEntry(R.string.user_server_op, mContext.getString(R.string.user_server_op_desc));
            if (mConnection != null && mConnection.isTrustedService(info.getNick(),
                    info.getUser(), info.getHost()))
                mConnection.rememberServiceNick(info.getNick());
        }
        if (info.isConnectionSecure())
            addEntry(R.string.user_secure, mContext.getString(R.string.user_secure_desc));
        if (mAdapter != null)
            mAdapter.notifyDataSetChanged();
        resolveManualChannel(info);
        refreshChannelState();
    }

    private void requestWhowas(String nick) {
        if (mConnection == null || !(mConnection.getApiInstance() instanceof IRCConnection))
            return;
        IRCConnection connection = (IRCConnection) mConnection.getApiInstance();
        WhowasCommandHandler handler = connection.getServerConnectionData()
                .getCommandHandlerList().getHandler(WhowasCommandHandler.class);
        if (handler == null) {
            handler = new WhowasCommandHandler();
            connection.getServerConnectionData().getCommandHandlerList().registerHandler(handler);
        }
        handler.request(nick, new WhowasCommandHandler.Callback() {
            @Override public void onResult(WhowasCommandHandler.Result result) {
                if (mRecyclerView != null)
                    mRecyclerView.post(() -> setHistoricalData(result));
                else
                    setHistoricalData(result);
            }
            @Override public void onError(String message) {
                if (mRecyclerView != null)
                    mRecyclerView.post(() -> Toast.makeText(mContext, message, Toast.LENGTH_LONG).show());
            }
        });
        connection.sendCommandRaw("WHOWAS " + nick, null, null);
    }

    private void setHistoricalData(WhowasCommandHandler.Result result) {
        mEntries.clear();
        mHistoricalData = true;
        mHost = result.host;
        mAccount = null;
        setUser(result.nick, result.user, result.realName, true);
        addEntry(R.string.operator_historical_data,
                mContext.getString(R.string.operator_historical_data_desc));
        addEntry(R.string.user_hostname, result.host);
        if (mAdapter != null)
            mAdapter.notifyDataSetChanged();
        refreshChannelState();
    }

    private String formatTime(int seconds) {
        if (seconds >= TimeUnit.DAYS.toSeconds(2L)) {
            int days = (int) TimeUnit.SECONDS.toDays(seconds);
            return mContext.getResources().getQuantityString(R.plurals.time_days, days, days);
        }
        if (seconds >= TimeUnit.HOURS.toSeconds(2L)) {
            int days = (int) TimeUnit.SECONDS.toHours(seconds);
            return mContext.getResources().getQuantityString(R.plurals.time_hours, days, days);
        }
        if (seconds >= TimeUnit.MINUTES.toSeconds(2L)) {
            int days = (int) TimeUnit.SECONDS.toMinutes(seconds);
            return mContext.getResources().getQuantityString(R.plurals.time_minutes, days, days);
        }
        return mContext.getResources().getQuantityString(R.plurals.time_seconds, seconds, seconds);
    }

    public void setUser(String nick, String user, String realName, boolean away) {
        mNick = nick;
        mUser = user;
        mRealName = realName;
        mAway = away;
        updateDialogStatusBarColor();
        if (mHeader != null)
            mHeader.bind();
    }

    public void addEntry(int titleId, String value) {
        addEntry(mContext.getString(titleId), value);
    }

    public void addEntry(String title, String value) {
        mEntries.add(new Pair<>(title, value));
        if (mAdapter != null)
            mAdapter.notifyItemInserted(mEntries.size() - 1);
    }

    private void resolveManualChannel(WhoisInfo info) {
        if (mSourceChannelExplicit || mConnection == null || info.getChannels() == null)
            return;
        Set<String> targetChannels = new HashSet<>();
        for (WhoisInfo.ChannelWithNickPrefixes item : info.getChannels())
            targetChannels.add(item.getChannel().toLowerCase());
        List<String> eligible = new ArrayList<>();
        if (mConnection.getApiInstance() instanceof IRCConnection) {
            ServerConnectionData data = ((IRCConnection) mConnection.getApiInstance())
                    .getServerConnectionData();
            for (String channel : data.getJoinedChannelList()) {
                if (targetChannels.contains(channel.toLowerCase()) && hasOperatorPrivileges(channel) &&
                        isNickPresent(channel, mNick))
                    eligible.add(channel);
            }
        }
        if (eligible.size() == 1) {
            mSourceChannel = eligible.get(0);
            mPendingChannelChoices = null;
        } else if (eligible.size() > 1) {
            mSourceChannel = null;
            mPendingChannelChoices = eligible;
        } else {
            mSourceChannel = null;
            mPendingChannelChoices = null;
        }
    }

    private void refreshChannelState() {
        mTargetPresent = mSourceChannel != null && isNickPresent(mSourceChannel, mNick);
        if (mDialog != null) {
            View button = mDialog.findViewById(R.id.operator_button);
            if (button != null)
                button.setVisibility(mSourceChannel != null && hasOperatorPrivileges(mSourceChannel)
                        ? View.VISIBLE : View.GONE);
        }
    }

    private boolean hasOperatorPrivileges(String channel) {
        if (mConnection == null || !(mConnection.getApiInstance() instanceof IRCConnection))
            return false;
        IRCConnection irc = (IRCConnection) mConnection.getApiInstance();
        String ownNick = irc.getServerConnectionData().getUserNick();
        NickWithPrefix own = findMember(channel, ownNick);
        if (own == null || own.getNickPrefixes() == null)
            return false;
        String prefixes = own.getNickPrefixes().toString();
        ModeList supportedPrefixes = irc.getServerConnectionData().getSupportList()
                .getSupportedNickPrefixes();
        ModeList supportedModes = irc.getServerConnectionData().getSupportList()
                .getSupportedNickPrefixModes();
        for (int i = 0; i < prefixes.length(); i++) {
            char prefix = prefixes.charAt(i);
            int index = supportedPrefixes.find(prefix);
            if (index >= 0 && index < supportedModes.length()) {
                char mode = supportedModes.get(index);
                if (mode == 'h' || mode == 'o' || mode == 'a' || mode == 'q')
                    return true;
            }
            if (prefix == '%' || prefix == '@' || prefix == '&' || prefix == '~')
                return true;
        }
        return false;
    }

    private boolean isNickPresent(String channel, String nick) {
        return findMember(channel, nick) != null;
    }

    private NickWithPrefix findMember(String channel, String nick) {
        if (channel == null || nick == null || mConnection == null ||
                !(mConnection.getApiInstance() instanceof IRCConnection))
            return null;
        try {
            ChannelData channelData = ((IRCConnection) mConnection.getApiInstance())
                    .getServerConnectionData().getJoinedChannelData(channel);
            for (NickWithPrefix member : channelData.getMembersAsNickPrefixList()) {
                if (nick.equalsIgnoreCase(member.getNick()))
                    return member;
            }
        } catch (NoSuchChannelException | RuntimeException ignored) { }
        return null;
    }

    private void showChannelChooser() {
        if (mPendingChannelChoices == null || mPendingChannelChoices.isEmpty())
            return;
        String[] channels = mPendingChannelChoices.toArray(new String[0]);
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.operator_choose_channel)
                .setItems(channels, (dialog, which) -> {
                    mSourceChannel = channels[which];
                    mPendingChannelChoices = null;
                    refreshChannelState();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void create() {
        View view = LayoutInflater.from(mContext).inflate(R.layout.dialog_bottom_user, null);

        mRecyclerView = view.findViewById(R.id.list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));
        mRecyclerView.addItemDecoration(new AdvancedDividerItemDecoration(mContext));

        mHeader = new HeaderHelper(view.findViewById(R.id.header));
        mHeader.bind();

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                mHeader.updateScrollY();
            }
        });

        mAdapter = new ItemAdapter();
        mRecyclerView.setAdapter(mAdapter);

        View operatorButton = view.findViewById(R.id.operator_button);
        operatorButton.setVisibility(mSourceChannel != null && hasOperatorPrivileges(mSourceChannel)
                ? View.VISIBLE : View.GONE);
        operatorButton.setOnClickListener((View v) -> showOperatorMenu());

        View fileButton = view.findViewById(R.id.file_button);
        fileButton.setVisibility(SharingSettings.hasAnySendOption(mContext)
                ? View.VISIBLE : View.GONE);
        fileButton.setOnClickListener(v -> {
            if (mContext instanceof MainActivity && mConnection != null &&
                    isSafeIrcParameter(mNick)) {
                SimosnapSendMenu.show((MainActivity) mContext, mConnection, mNick);
                mDialog.cancel();
            }
        });

        view.findViewById(R.id.message_button).setOnClickListener((View v) -> {
            List<String> l = new ArrayList<>();
            l.add(mNick);
            mConnection.getApiInstance().joinChannels(l, (Void vo) -> {
                view.post(() -> {
                    if (mContext instanceof MainActivity)
                        ((MainActivity) mContext).openServer(mConnection, mNick);
                    mDialog.cancel();
                });
            }, null);
        });

        mDialog = new StatusBarColorBottomSheetDialog(mContext);
        mDialog.setContentView(view);
        int compatMaxHeight = mContext.getResources().getDimensionPixelSize(R.dimen.dialog_bottom_user_header_height_compact_activate);
        mDialog.getWindow().getDecorView().addOnLayoutChangeListener((View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) -> {
            if (bottom - top == oldBottom - oldTop)
                return;
            view.post(() -> {
                BottomSheetBehavior behaviour = BottomSheetBehavior.from(mDialog.
                        findViewById(R.id.design_bottom_sheet));
                view.setMinimumHeight(bottom - top);
                mHeader.setCompactMode(behaviour.getPeekHeight() < compatMaxHeight);
            });
        });
        updateDialogStatusBarColor();
    }

    private void showOperatorMenu() {
        if (mSourceChannel == null || mConnection == null)
            return;
        mTargetPresent = isNickPresent(mSourceChannel, mNick);
        if (!hasOperatorPrivileges(mSourceChannel)) {
            Toast.makeText(mContext, R.string.operator_not_authorised, Toast.LENGTH_LONG).show();
            refreshChannelState();
            return;
        }

        boolean hostAvailable = isSafeIrcParameter(mHost);
        boolean identAvailable = isSafeIrcParameter(mUser);
        MenuBottomSheetDialog menu = new MenuBottomSheetDialog(mContext);
        menu.addHeader(mContext.getString(R.string.operator_actions_for, mSourceChannel));
        menu.addItem(mContext.getString(R.string.operator_kick), R.drawable.ic_close,
                mTargetPresent, item -> {
            showKickDialog(false);
            return true;
        });
        menu.addItem(mContext.getString(R.string.operator_kickban), R.drawable.ic_delete,
                hostAvailable, item -> {
                    showKickDialog(true);
                    return true;
                });
        menu.addItem(mContext.getString(R.string.operator_tban), R.drawable.ic_history,
                hostAvailable, item -> {
                    showTbanDialog();
                    return true;
                });
        menu.addItem(mContext.getString(R.string.operator_mute), R.drawable.ic_lock_small,
                hostAvailable, item -> {
                    showSimpleConfirmation(R.string.operator_mute,
                            "MODE " + mSourceChannel + " +b m:*!*@" + mHost);
                    return true;
                });
        menu.addItem(mContext.getString(R.string.operator_audi), R.drawable.ic_user,
                identAvailable, item -> {
                    showSimpleConfirmation(R.string.operator_audi,
                            "MODE " + mSourceChannel + " +b u:*!" + mUser + "@*");
                    return true;
                });
        menu.addItem(mContext.getString(R.string.operator_unban_host), R.drawable.ic_refresh,
                hostAvailable, item -> {
                    showMaskCommandDialog(R.string.operator_unban_host,
                            "MODE " + mSourceChannel + " -b ", "*!*@" + mHost);
                    return true;
                });
        menu.addItem(mContext.getString(R.string.operator_voice), R.drawable.ic_add_circle_outline,
                mTargetPresent, item -> {
                    showSimpleConfirmation(R.string.operator_voice,
                            "MODE " + mSourceChannel + " +v " + mNick);
                    return true;
                });
        menu.show();
    }

    private void showKickDialog(boolean kickban) {
        if (!isSafeIrcParameter(mNick) || (!mTargetPresent && !kickban) ||
                (kickban && !isSafeIrcParameter(mHost)))
            return;

        EditText reason = new EditText(mContext);
        reason.setHint(R.string.operator_reason_optional);
        reason.setSingleLine(true);
        int actionId = kickban ? R.string.operator_kickban : R.string.operator_kick;
        new AlertDialog.Builder(mContext)
                .setTitle(actionId)
                .setMessage(mContext.getString(R.string.operator_confirm_action,
                        mContext.getString(actionId), mNick, mSourceChannel))
                .setView(kickban ? createReasonInput(reason) : reason)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    String kickCommand = "KICK " + mSourceChannel + " " + mNick;
                    String value = sanitiseReason(reason.getText().toString());
                    if (!value.isEmpty())
                        kickCommand += " :" + value;
                    if (kickban && mTargetPresent) {
                        // Some networks enforce a newly set ban immediately. Kick first so the
                        // operator-provided reason is not lost with a subsequent 441 reply.
                        sendRawCommand(kickCommand);
                        sendRawCommand("MODE " + mSourceChannel + " +b *!*@" + mHost);
                    } else if (kickban) {
                        sendRawCommand("MODE " + mSourceChannel + " +b *!*@" + mHost);
                    } else if (mTargetPresent) {
                        sendRawCommand(kickCommand);
                    }
                })
                .show();
    }

    private void showTbanDialog() {
        if (!isSafeIrcParameter(mHost))
            return;

        LinearLayout fields = new LinearLayout(mContext);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * mContext.getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, 0);
        EditText duration = new EditText(mContext);
        duration.setHint(R.string.operator_duration);
        duration.setText("3h");
        duration.setSelectAllOnFocus(true);
        duration.setSingleLine(true);
        duration.setInputType(InputType.TYPE_CLASS_TEXT);
        EditText mask = new EditText(mContext);
        mask.setHint(R.string.operator_mask);
        mask.setText("*!*@" + mHost);
        mask.setSingleLine(true);
        EditText reason = new EditText(mContext);
        reason.setHint(R.string.operator_reason_optional);
        reason.setSingleLine(true);
        fields.addView(duration);
        fields.addView(mask);
        fields.addView(createReasonInput(reason, false));
        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setTitle(R.string.operator_tban)
                .setMessage(mContext.getString(R.string.operator_confirm_action,
                        mContext.getString(R.string.operator_tban), mNick, mSourceChannel))
                .setView(fields)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = duration.getText().toString().trim();
                    if (!value.matches("(?i)[1-9][0-9]*[smhdw]")) {
                        duration.setError(mContext.getString(R.string.operator_invalid_duration));
                        return;
                    }
                    String maskValue = mask.getText().toString().trim();
                    if (!isSafeIrcParameter(maskValue)) {
                        mask.setError(mContext.getString(R.string.operator_invalid_mask));
                        return;
                    }
                    if (mTargetPresent && isSafeIrcParameter(mNick)) {
                        String kickCommand = "KICK " + mSourceChannel + " " + mNick;
                        String reasonValue = sanitiseReason(reason.getText().toString());
                        if (!reasonValue.isEmpty())
                            kickCommand += " :" + reasonValue;
                        sendRawCommand(kickCommand);
                    }
                    sendRawCommand("TBAN " + mSourceChannel + " " + value + " " + maskValue);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private View createReasonInput(EditText reason) {
        return createReasonInput(reason, true);
    }

    private View createReasonInput(EditText reason, boolean addPadding) {
        LinearLayout container = new LinearLayout(mContext);
        container.setOrientation(LinearLayout.VERTICAL);
        if (addPadding) {
            int padding = (int) (20 * mContext.getResources().getDisplayMetrics().density);
            container.setPadding(padding, 0, padding, 0);
        }
        List<String> reasons = OperatorReasonSettings.getReasons(mContext);
        if (!reasons.isEmpty()) {
            List<String> options = new ArrayList<>();
            options.add(mContext.getString(R.string.operator_custom_reason));
            options.addAll(reasons);
            Spinner spinner = new Spinner(mContext);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(mContext,
                    R.layout.simple_spinner_item, android.R.id.text1, options);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                           int position, long id) {
                    if (position > 0) {
                        reason.setText(options.get(position));
                        reason.setSelection(reason.length());
                    }
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
            container.addView(spinner);
        }
        container.addView(reason);
        return container;
    }

    private void showMaskCommandDialog(int actionId, String commandPrefix, String defaultMask) {
        EditText mask = new EditText(mContext);
        mask.setHint(R.string.operator_mask);
        mask.setText(defaultMask);
        mask.setSelectAllOnFocus(true);
        mask.setSingleLine(true);
        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setTitle(actionId)
                .setMessage(mContext.getString(R.string.operator_confirm_action,
                        mContext.getString(actionId), mNick, mSourceChannel))
                .setView(mask)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = mask.getText().toString().trim();
                    if (!isSafeIrcParameter(value)) {
                        mask.setError(mContext.getString(R.string.operator_invalid_mask));
                        return;
                    }
                    sendRawCommand(commandPrefix + value);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void showSimpleConfirmation(int actionId, String command) {
        if (!isSafeIrcParameter(mNick))
            return;
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.operator_confirm_title)
                .setMessage(mContext.getString(R.string.operator_confirm_action,
                        mContext.getString(actionId), mNick, mSourceChannel))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok,
                        (dialog, which) -> sendRawCommand(command))
                .show();
    }

    private void sendRawCommand(String command) {
        if (mConnection == null || !(mConnection.getApiInstance() instanceof IRCConnection))
            return;
        ((IRCConnection) mConnection.getApiInstance()).sendCommandRaw(command, null, null);
        Toast.makeText(mContext, R.string.operator_command_sent, Toast.LENGTH_SHORT).show();
    }

    private static boolean isChannelName(String value) {
        if (value == null || value.length() < 2)
            return false;
        char prefix = value.charAt(0);
        return prefix == '#' || prefix == '&' || prefix == '+' || prefix == '!';
    }

    private static boolean isSafeIrcParameter(String value) {
        return value != null && !value.isEmpty() && !value.matches(".*[\\s\\r\\n].*");
    }

    private static String sanitiseReason(String value) {
        return value.trim().replace('\r', ' ').replace('\n', ' ');
    }

    private void updateDialogStatusBarColor() {
        if (mDialog == null)
            return;
        if (mAway)
            mDialog.setStatusBarColor(ContextCompat.getColor(mContext, R.color.userAwayColorPrimaryDark));
        else
            mDialog.setStatusBarColor(ContextCompat.getColor(mContext, R.color.colorPrimaryDark));
    }

    public BottomSheetDialog show() {
        if (mDialog == null)
            create();
        mDialog.show();
        if (mPendingChannelChoices != null)
            mDialog.getWindow().getDecorView().post(this::showChannelChooser);
        return mDialog;
    }

    private class ItemAdapter extends RecyclerView.Adapter {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.dialog_bottom_user_entry, parent, false);
            return new EntryHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((EntryHolder) holder).bind(mEntries.get(position));
        }

        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        private class EntryHolder extends RecyclerView.ViewHolder {
            private TextView mTitle;
            private TextView mValue;

            public EntryHolder(View itemView) {
                super(itemView);
                mTitle = itemView.findViewById(R.id.title);
                mValue = itemView.findViewById(R.id.value);
                itemView.setOnLongClickListener((View v) -> {
                    copyValueToClipboard(v.getContext(), mTitle.getText(), mValue.getText());
                    return true;
                });
            }

            public void bind(Pair<String, String> entry) {
                mTitle.setText(entry.first);
                mValue.setText(entry.second);
            }
        }

    }

    private class HeaderHelper {
        private View mContainer;
        private TextView mName;
        private TextView mNick;
        private TextView mUser;
        private ImageView mAvatar;
        private int mBottomMargin;
        private int mNameBottomMargin;
        private int mTargetNameBottomMargin;
        private int mMaxHeight;
        private int mMinHeight;
        private int mElevation;
        private int mCurrentCollapse;
        private boolean mAvatarLoaded;
        private boolean mCompactMode = false;

        public HeaderHelper(View itemView) {
            mContainer = itemView;
            mName = itemView.findViewById(R.id.name);
            mNick = itemView.findViewById(R.id.nick);
            mUser = itemView.findViewById(R.id.user);
            mAvatar = itemView.findViewById(R.id.avatar);
            setCompactMode(false, true);
            mElevation = mContext.getResources().getDimensionPixelSize(R.dimen.abc_action_bar_elevation_material);

            mNick.setOnLongClickListener(createLongClickListener(mContext, R.string.user_nick));
            mUser.setOnLongClickListener(createLongClickListener(mContext, R.string.server_user));
            mName.setOnLongClickListener(createLongClickListener(mContext, R.string.server_realname));
        }

        public void bind() {
            if (mAway) {
                mName.setText(mContext.getString(R.string.user_title_away, mRealName));
                mContainer.setBackgroundResource(R.color.userAwayColorPrimary);
            } else {
                mName.setText(UserBottomSheetDialog.this.mRealName);
                mContainer.setBackgroundResource(R.color.colorPrimary);
            }
            mNick.setText(UserBottomSheetDialog.this.mNick);
            mUser.setText(UserBottomSheetDialog.this.mUser);
            SimosnapAvatarLoader.load(mAvatar, mAccount, true, loaded -> {
                mAvatarLoaded = loaded;
                updateAvatarLayout(loaded && mCurrentCollapse == 0 && !mCompactMode);
            });
        }

        public void setScrollY(int y) {
            int cy = Math.min(y, mMaxHeight - mMinHeight);
            if (y == -1) {
                cy = mMaxHeight - mMinHeight;
                mNick.setVisibility(View.GONE);
                mUser.setVisibility(View.GONE);
            } else {
                mNick.setVisibility(View.VISIBLE);
                mUser.setVisibility(View.VISIBLE);
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mNick.getLayoutParams();
                params.bottomMargin = mBottomMargin - y;
                mNick.setLayoutParams(params);
                params = (RelativeLayout.LayoutParams) mUser.getLayoutParams();
                params.bottomMargin = mBottomMargin - y;
                mUser.setLayoutParams(params);
            }
            mCurrentCollapse = cy;
            updateAvatarLayout(mAvatarLoaded && cy == 0 && !mCompactMode);
            ViewCompat.setElevation(mContainer, cy == (mMaxHeight - mMinHeight) ? mElevation : 0);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mName.getLayoutParams();
            params.bottomMargin = Math.max(mNameBottomMargin - cy, mTargetNameBottomMargin);
            mName.setLayoutParams(params);
            params = (RelativeLayout.LayoutParams) mContainer.getLayoutParams();
            int newH = mMaxHeight - cy;
            if (newH != params.height) {
                params.height = mMaxHeight - cy;
                mContainer.setLayoutParams(params);
            }
        }

        public void updateScrollY() {
            if (mRecyclerView == null || mRecyclerView.getChildCount() == 0) {
                setScrollY(0);
                return;
            }
            View v = mRecyclerView.getChildAt(0);
            if (mRecyclerView.getChildAdapterPosition(v) != 0) {
                setScrollY(-1);
                return;
            }
            setScrollY(mRecyclerView.getPaddingTop() - v.getTop());
        }

        private void setCompactMode(boolean compactMode, boolean force) {
            if (mCompactMode == compactMode && !force)
                return;
            mCompactMode = compactMode;
            if (compactMode) {
                mMaxHeight = mContext.getResources().getDimensionPixelSize(R.dimen.dialog_bottom_user_header_height_compact);
                mMinHeight = mContext.getResources().getDimensionPixelSize(R.dimen.dialog_bottom_user_header_min_height_compact);
                mBottomMargin = mContext.getResources().getDimensionPixelSize(R.dimen.dialog_bottom_user_header_bottom_margin_compact);
                mNameBottomMargin = mContext.getResources().getDimensionPixelSize(R.dimen.dialog_bottom_user_header_name_bottom_margin_compact);
                mTargetNameBottomMargin = mContext.getResources().getDimensionPixelSize(R.dimen.dialog_bottom_user_header_name_bottom_margin_target_compact);
            } else {
                mMaxHeight = mContext.getResources().getDimensionPixelSize(R.dimen.dialog_bottom_user_header_height);
                mMinHeight = mContext.getResources().getDimensionPixelSize(R.dimen.dialog_bottom_user_header_min_height);
                mBottomMargin = mContext.getResources().getDimensionPixelSize(R.dimen.dialog_bottom_user_header_bottom_margin);
                mNameBottomMargin = mContext.getResources().getDimensionPixelSize(R.dimen.dialog_bottom_user_header_name_bottom_margin);
                mTargetNameBottomMargin = mContext.getResources().getDimensionPixelSize(R.dimen.dialog_bottom_user_header_name_bottom_margin_target);
            }
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mContainer.getLayoutParams();
            params.height = mMaxHeight;
            mContainer.setLayoutParams(params);
            mRecyclerView.setPadding(0, mMaxHeight, 0, 0);
            ((LinearLayoutManager) mRecyclerView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
            setScrollY(0);
        }

        public void setCompactMode(boolean compactMode) {
            setCompactMode(compactMode, false);
        }

        private void updateAvatarLayout(boolean show) {
            mAvatar.setVisibility(show ? View.VISIBLE : View.GONE);
        }

    }

    private static void copyValueToClipboard(Context context, CharSequence key, CharSequence value) {
        ClipboardManager clipboard = (ClipboardManager) context
                .getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(key, value);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, context.getString(R.string.user_info_copied, key),
                Toast.LENGTH_SHORT).show();
    }

    private static View.OnLongClickListener createLongClickListener(Context context, int resId) {
        return (View v) -> {
            copyValueToClipboard(context, context.getString(resId), ((TextView) v).getText());
            return true;
        };
    }

}
