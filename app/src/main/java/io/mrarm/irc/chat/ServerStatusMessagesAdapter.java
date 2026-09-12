package io.mrarm.irc.chat;

import android.app.Dialog;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import androidx.recyclerview.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.mrarm.chatlib.dto.StatusMessageInfo;
import io.mrarm.chatlib.dto.StatusMessageList;
import io.mrarm.chatlib.dto.WhoisStatusMessageInfo;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.irc.MainActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.dialog.UserBottomSheetDialog;
import io.mrarm.irc.dialog.NicknameContextMenu;
import io.mrarm.irc.dialog.MenuBottomSheetDialog;
import io.mrarm.irc.irc.CallerIdAcceptManager;
import io.mrarm.irc.irc.CallerIdStatusMessageInfo;
import io.mrarm.irc.irc.WhowasStatusMessageInfo;
import io.mrarm.irc.view.WhowasRecordView;
import io.mrarm.irc.util.AlignToPointSpan;
import io.mrarm.irc.util.IRCColorUtils;
import io.mrarm.irc.util.MessageBuilder;
import io.mrarm.irc.util.SelectableLinkMovementMethod;
import io.mrarm.irc.util.LongClickableSpan;

public class ServerStatusMessagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_MESSAGE = 0;
    private static final int TYPE_EXPANDABLE_MESSAGE = 1;
    private static final int TYPE_WHOWAS = 2;
    private static final Pattern ACCEPT_LIST_CONFIRMATION = Pattern.compile(
            "^([^\\s:]+)\\s+is now on your accept list(?:\\.|$)", Pattern.CASE_INSENSITIVE);

    private ServerConnectionInfo mConnection;
    private StatusMessageList mMessages;
    private Set<StatusMessageInfo> mExpandedMessages;
    private Typeface mTypeface;
    private int mFontSize;

    public ServerStatusMessagesAdapter(ServerConnectionInfo connection,
                                       StatusMessageList messages) {
        mConnection = connection;
        setMessages(messages);
    }

    public void setMessageFont(Typeface typeface, int textSize) {
        mTypeface = typeface;
        mFontSize = textSize;
    }

    public void setMessages(StatusMessageList messages) {
        this.mMessages = messages;
        mExpandedMessages = new HashSet<>();
        notifyDataSetChanged();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        if (viewType == TYPE_MESSAGE) {
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.chat_message, viewGroup, false);
            return new MessageHolder(view);
        } else if (viewType == TYPE_EXPANDABLE_MESSAGE) {
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.chat_expandable_message, viewGroup, false);
            return new ExpandableMessageHolder(view, this);
        } else if (viewType == TYPE_WHOWAS) {
            return new WhowasHolder(new WhowasRecordView(viewGroup.getContext()));
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        int viewType = holder.getItemViewType();
        if (viewType == TYPE_MESSAGE) {
            ((MessageHolder) holder).bind(mMessages.getMessages().get(position));
        } else if (viewType == TYPE_EXPANDABLE_MESSAGE) {
            ((ExpandableMessageHolder) holder).bind(position,
                    mMessages.getMessages().get(position));
        } else if (viewType == TYPE_WHOWAS) {
            ((WhowasHolder) holder).bind((WhowasStatusMessageInfo)
                    mMessages.getMessages().get(position));
        }
    }

    private void toggleExpandItem(int position) {
        StatusMessageInfo info = mMessages.getMessages().get(position);
        if (mExpandedMessages.contains(info))
            mExpandedMessages.remove(info);
        else
            mExpandedMessages.add(info);
        notifyItemChanged(position);
    }

    @Override
    public int getItemCount() {
        return mMessages.getMessages().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (mMessages.getMessages().get(position) instanceof WhowasStatusMessageInfo)
            return TYPE_WHOWAS;
        StatusMessageInfo.MessageType type = mMessages.getMessages().get(position).getType();
        if (type == StatusMessageInfo.MessageType.MOTD ||
                type == StatusMessageInfo.MessageType.WHOIS)
            return TYPE_EXPANDABLE_MESSAGE;
        return TYPE_MESSAGE;
    }

    public class WhowasHolder extends RecyclerView.ViewHolder {
        private final WhowasRecordView recordView;
        WhowasHolder(WhowasRecordView view) {
            super(view);
            recordView = view;
        }
        void bind(WhowasStatusMessageInfo info) {
            recordView.bind(mConnection, info.getResult());
        }
    }

    public class MessageHolder extends RecyclerView.ViewHolder {

        private TextView mText;

        public MessageHolder(View v) {
            super(v);
            mText = v.findViewById(R.id.chat_message);
            configureSelectableText(mText);
            v.setOnLongClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION)
                    return false;
                StatusMessageInfo message = mMessages.getMessages().get(position);
                if (!mConnection.isKnownServiceNick(message.getSender()))
                    return false;
                showServiceMessageMenu(view, message);
                return true;
            });
        }

        public void bind(StatusMessageInfo message) {
            if (mTypeface != null)
                mText.setTypeface(mTypeface);
            if (mFontSize != -1)
                mText.setTextSize(TypedValue.COMPLEX_UNIT_SP, mFontSize);

            Context context = mText.getContext();
            if (message.getType() == StatusMessageInfo.MessageType.DISCONNECT_WARNING) {
                mText.setText(AlignToPointSpan.apply(mText, MessageBuilder.getInstance(context)
                        .buildDisconnectWarning(message.getDate())));
                return;
            }
            if (message instanceof CallerIdStatusMessageInfo) {
                mText.setText(AlignToPointSpan.apply(mText,
                        buildCallerIdMessage(context, (CallerIdStatusMessageInfo) message)));
                return;
            }
            CharSequence built = MessageBuilder.getInstance(context).buildStatusMessage(message,
                    createServiceClickSpan(message));
            built = addAcceptConfirmationNickLink(built, message.getMessage());
            mText.setText(AlignToPointSpan.apply(mText, built));
        }

    }

    private CharSequence addAcceptConfirmationNickLink(CharSequence built, String rawMessage) {
        if (built == null || rawMessage == null)
            return built;
        Matcher matcher = ACCEPT_LIST_CONFIRMATION.matcher(rawMessage);
        if (!matcher.find())
            return built;
        final String nick = matcher.group(1);
        CallerIdAcceptManager.noteAccepted(mConnection, nick, true);
        String rendered = built.toString();
        int start = rendered.indexOf(nick);
        if (start < 0)
            return built;
        SpannableStringBuilder text = new SpannableStringBuilder(built);
        text.setSpan(createPrivateChatNickSpan(nick), start, start + nick.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private ClickableSpan createPrivateChatNickSpan(final String nick) {
        return new LongClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                mConnection.addStoredConversation(nick);
                if (widget.getContext() instanceof MainActivity)
                    ((MainActivity) widget.getContext()).openServer(mConnection, nick);
            }

            @Override
            public boolean onLongClick(@NonNull View widget) {
                NicknameContextMenu.show(widget.getContext(), mConnection, nick, null);
                return true;
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setUnderlineText(false);
            }
        };
    }

    private CharSequence buildCallerIdMessage(Context context, CallerIdStatusMessageInfo message) {
        SpannableStringBuilder text = new SpannableStringBuilder(
                MessageBuilder.getInstance(context).buildStatusMessage(message));
        text.append(" ");
        int start = text.length();
        text.append("[ACCETTA]");
        text.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                CallerIdAcceptManager.setAccepted(mConnection, message.getNick(), true);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(true);
            }
        }, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private void showServiceMessageMenu(View view, StatusMessageInfo message) {
        Context context = view.getContext();
        MenuBottomSheetDialog menu = new MenuBottomSheetDialog(context);
        menu.addItem(R.string.action_copy, R.drawable.ic_content_copy, item -> {
            io.mrarm.irc.util.ClipboardUtils.copyPlainText(context,
                    message.getSender(), message.getMessage());
            return true;
        });
        menu.addItem(R.string.service_open_private_chat, R.drawable.ic_message, item -> {
            mConnection.addStoredConversation(message.getSender());
            if (context instanceof MainActivity)
                ((MainActivity) context).openServer(mConnection, message.getSender());
            return true;
        });
        menu.show();
        if (context instanceof MainActivity)
            ((MainActivity) context).setFragmentDialog(menu);
    }

    private ClickableSpan createServiceClickSpan(StatusMessageInfo message) {
        final String nick = message.getSender();
        if (!mConnection.isKnownServiceNick(nick))
            return null;
        return createPrivateChatNickSpan(nick);
    }

    public class ExpandableMessageHolder extends RecyclerView.ViewHolder {

        private ServerStatusMessagesAdapter mAdapter;
        private TextView mText;
        private TextView mExpandedText;
        private ImageView mExpandIcon;
        private int mPosition;

        public ExpandableMessageHolder(View v, ServerStatusMessagesAdapter adapter) {
            super(v);
            mAdapter = adapter;
            mText = v.findViewById(R.id.chat_message);
            mExpandedText = v.findViewById(R.id.chat_expanded_message);
            mExpandIcon = v.findViewById(R.id.expand_icon);
            //setExpanded(true);

            mExpandedText.setTypeface(Typeface.MONOSPACE);
            configureSelectableText(mText);
            configureSelectableText(mExpandedText);

            v.setOnClickListener((View view) -> {
                StatusMessageInfo msg = mMessages.getMessages().get(mPosition);
                if (msg instanceof WhoisStatusMessageInfo) {
                    UserBottomSheetDialog dialog = new UserBottomSheetDialog(view.getContext());
                    dialog.setConnection(mConnection);
                    dialog.setData(((WhoisStatusMessageInfo) msg).getWhoisInfo());
                    Dialog d = dialog.show();
                    if (view.getContext() instanceof MainActivity)
                        ((MainActivity) view.getContext()).setFragmentDialog(d);
                } else {
                    mAdapter.toggleExpandItem(mPosition);
                }
            });
        }

        public void bind(int pos, StatusMessageInfo message) {
            if (mTypeface != null)
                mText.setTypeface(mTypeface);
            if (mFontSize != -1) {
                mText.setTextSize(TypedValue.COMPLEX_UNIT_SP, mFontSize);
                mExpandedText.setTextSize(TypedValue.COMPLEX_UNIT_SP, mFontSize);
            }

            this.mPosition = pos;

            mText.setText(AlignToPointSpan.apply(mText,
                    MessageBuilder.getInstance(mText.getContext()).buildStatusMessage(message)));

            boolean expanded = mAdapter.mExpandedMessages.contains(message);

            mExpandedText.setVisibility(expanded ? View.VISIBLE : View.GONE);
            mExpandIcon.setRotation(expanded ? 180.f : 0.f);

            if (!expanded)
                return;
            mExpandedText.setText(IRCColorUtils.getFormattedString(mText.getContext(),
                    message.getMessage()));
        }

    }

    private static void configureSelectableText(TextView text) {
        text.setTextIsSelectable(true);
        text.setMovementMethod(SelectableLinkMovementMethod.getInstance());
    }

}
