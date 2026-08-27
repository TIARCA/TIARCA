package io.mrarm.irc.chat;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.app.Dialog;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import io.mrarm.chatlib.dto.MessageId;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.irc.NotificationManager;
import io.mrarm.irc.MainActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.util.AlignToPointSpan;
import io.mrarm.irc.util.MessageBuilder;
import io.mrarm.irc.dialog.UserBottomSheetDialog;
import io.mrarm.irc.dialog.NicknameContextMenu;
import io.mrarm.irc.util.LongClickableSpan;
import io.mrarm.irc.util.SelectableLinkMovementMethod;
import io.mrarm.irc.util.StyledAttributesHelper;

public class ChatMessagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
        implements ChatSelectTouchListener.AdapterInterface {

    private static final int TYPE_MESSAGE = 0;
    private static final int TYPE_DAY_MARKER = 1;
    private static final int TYPE_MESSAGE_WITH_NEW_MESSAGE_MARKER = 2;
    private static final int MAX_LIVE_MESSAGE_ITEMS = 500;

    private ChatMessagesFragment mFragment;
    private List<Item> mMessages;
    private List<Item> mPrependedMessages;
    private ChatSelectTouchListener mSelectListener;
    private Drawable mMessageFlashBackground;
    private Typeface mTypeface;
    private int mFontSize;
    private long mItemIdOffset = -1000000000L;

    // Used to display the day marker
    private int mFirstMessageDay = -1;
    private int mLastMessageDay = -1;

    private MessageId mNewMessagesStart;

    public ChatMessagesAdapter(ChatMessagesFragment fragment, List<MessageInfo> messages,
                               List<MessageId> messageIds) {
        mFragment = fragment;
        StyledAttributesHelper attributes = StyledAttributesHelper.obtainStyledAttributes(
                fragment.getContext(), new int[] { R.attr.colorControlHighlight });
        mMessageFlashBackground = new ColorDrawable(attributes.getColor(R.attr.colorControlHighlight,
                0));
        attributes.recycle();
        setMessages(messages, messageIds);
        setHasStableIds(true);
    }

    public void setNewMessagesStart(MessageId start) {
        int oldi = findMessageWithId(mNewMessagesStart);
        mNewMessagesStart = start;
        int i = findMessageWithId(start);
        if (oldi != -1)
            notifyItemChanged(oldi);
        if (i != -1)
            notifyItemChanged(i);
    }

    public MessageId getNewMessagesStart() {
        return mNewMessagesStart;
    }

    public void setMessageFont(Typeface typeface, int fontSize) {
        mTypeface = typeface;
        mFontSize = fontSize;
    }

    public Item getMessage(int index) {
        if (index < mPrependedMessages.size())
            return mPrependedMessages.get(mPrependedMessages.size() - 1 - index);
        index -= mPrependedMessages.size();
        if (index < mMessages.size())
            return mMessages.get(index);
        return null;
    }

    public int findMessageWithId(MessageId id) {
        if (id == null)
            return -1;
        for (int i = mPrependedMessages.size() - 1; i >= 0; --i) {
            Item it = mPrependedMessages.get(i);
            if (it instanceof MessageItem && ((MessageItem) it).mMessageId.equals(id))
                return mPrependedMessages.size() - 1 - i;
        }
        for (int i = mMessages.size() - 1; i >= 0; --i) {
            Item it = mMessages.get(i);
            if (it instanceof MessageItem && ((MessageItem) it).mMessageId.equals(id))
                return mPrependedMessages.size() + i;
        }
        return -1;
    }

    public void flashMessage(RecyclerView recyclerView, int position) {
        if (position < 0 || position >= getItemCount())
            return;
        long itemId = getItemId(position);
        setMessageFlashHighlighted(recyclerView, itemId, true);
        recyclerView.postDelayed(() -> setMessageFlashHighlighted(recyclerView, itemId, false), 300L);
        recyclerView.postDelayed(() -> setMessageFlashHighlighted(recyclerView, itemId, true), 550L);
        recyclerView.postDelayed(() -> setMessageFlashHighlighted(recyclerView, itemId, false), 850L);
        recyclerView.postDelayed(() -> setMessageFlashHighlighted(recyclerView, itemId, true), 1100L);
        recyclerView.postDelayed(() -> setMessageFlashHighlighted(recyclerView, itemId, false), 1550L);
    }

    private void setMessageFlashHighlighted(RecyclerView recyclerView, long itemId,
                                             boolean highlighted) {
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForItemId(itemId);
        if (holder != null)
            holder.itemView.setBackground(highlighted
                    ? mMessageFlashBackground.getConstantState().newDrawable() : null);
    }

    private void deleteMessageInternal(int index) {
        if (index < mPrependedMessages.size()) {
            mPrependedMessages.remove(mPrependedMessages.size() - 1 - index);
        } else {
            index -= mPrependedMessages.size();
            if (index < mMessages.size())
                mMessages.remove(index);
        }
    }

    private int appendMessageInternal(MessageInfo m, MessageId mi) {
        int ret = 0;
        int day = getDayInt(m.getDate());
        if (mFirstMessageDay == -1)
            mFirstMessageDay = day;
        if (day != mLastMessageDay) {
            mMessages.add(new DayMarkerItem(day));
            mLastMessageDay = day;
            ret++;
        }
        mMessages.add(new MessageItem(m, mi));
        ret++;
        return ret;
    }

    private int prependMessageInternal(MessageInfo m, MessageId mi) {
        int ret = 0;
        int day = getDayInt(m.getDate());
        if (mLastMessageDay == -1)
            mLastMessageDay = day;
        if (day != mFirstMessageDay) {
            mPrependedMessages.add(new DayMarkerItem(day));
            mFirstMessageDay = day;
            ret++;
        }
        mPrependedMessages.add(new MessageItem(m, mi));
        ret++;
        return ret;
    }

    public void appendMessage(MessageInfo m, MessageId mi) {
        int c = appendMessageInternal(m, mi);
        if (mPrependedMessages.isEmpty() && mMessages.size() > MAX_LIVE_MESSAGE_ITEMS) {
            while (mMessages.size() > MAX_LIVE_MESSAGE_ITEMS)
                mMessages.remove(0);
            while (mMessages.size() > 1 && mMessages.get(0) instanceof DayMarkerItem &&
                    mMessages.get(1) instanceof DayMarkerItem)
                mMessages.remove(0);
            if (!mMessages.isEmpty() && mMessages.get(0) instanceof MessageItem) {
                int day = getDayInt(((MessageItem) mMessages.get(0)).mMessage.getDate());
                mMessages.add(0, new DayMarkerItem(day));
                if (mMessages.size() > MAX_LIVE_MESSAGE_ITEMS)
                    mMessages.remove(1);
            }
            notifyDataSetChanged();
            return;
        }
        if (c == 1)
            notifyItemInserted(mMessages.size() - 1);
        else
            notifyItemRangeInserted(mMessages.size() - c, c);
    }

    public void setMessages(List<MessageInfo> messages, List<MessageId> messageIds) {
        mMessages = new ArrayList<>();
        mPrependedMessages = new ArrayList<>();
        int n = messages.size();
        for (int i = 0; i < n; i++)
            appendMessageInternal(messages.get(i), messageIds.get(i));
        notifyDataSetChanged();
    }

    /** Releases rendered message objects; the SQLite history remains untouched. */
    public void releaseMessages() {
        mMessages = new ArrayList<>();
        mPrependedMessages = new ArrayList<>();
        mFirstMessageDay = -1;
        mLastMessageDay = -1;
        notifyDataSetChanged();
    }

    public void addMessagesToTop(List<MessageInfo> messages, List<MessageId> messageIds) {
        if (messages.size() == 0)
            return;
        if (getMessage(0) instanceof DayMarkerItem) {
            deleteMessageInternal(0);
            notifyItemRangeRemoved(0, 1);
            mItemIdOffset -= 1;
        }
        int cnt = 0;
        for (int i = messages.size() - 1; i >= 0; --i)
            cnt += prependMessageInternal(messages.get(i), messageIds.get(i));
        mPrependedMessages.add(new DayMarkerItem(mFirstMessageDay));
        ++cnt;
        mItemIdOffset += cnt;
        notifyItemRangeInserted(0, cnt);
    }

    public void addMessagesToBottom(List<MessageInfo> messages, List<MessageId> messageIds) {
        if (messages.size() == 0)
            return;
        int appendAt = getItemCount();
        int cnt = 0;
        int n = messages.size();
        for (int i = 0; i < n; i++)
            cnt += appendMessageInternal(messages.get(i), messageIds.get(i));
        notifyItemRangeInserted(appendAt, cnt);
    }

    public boolean hasMessages() {
        return mMessages != null && (mMessages.size() > 0 || mPrependedMessages.size() > 0);
    }

    public void setSelectListener(ChatSelectTouchListener selectListener) {
        mSelectListener = selectListener;
    }

    public List<MessageId> getMessageIdsInRange(int start, int end) {
        List<MessageId> ret = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            Item item = getMessage(i);
            if (item instanceof MessageItem)
                ret.add(((MessageItem) item).mMessageId);
        }
        return ret;
    }

    public void hideMessagesInRange(int start, int end) {
        for (int i = start; i <= end; i++) {
            Item item = getMessage(i);
            if (item instanceof MessageItem)
                ((MessageItem) item).mHidden = true;
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        if (viewType == TYPE_MESSAGE) {
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.chat_message, viewGroup, false);
            return new MessageHolder(view);
        }
        if (viewType == TYPE_MESSAGE_WITH_NEW_MESSAGE_MARKER) {
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.chat_new_messages_marker, viewGroup, false);
            return new MessageHolder(view);
        }
        if (viewType == TYPE_DAY_MARKER) {
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.chat_day_marker, viewGroup, false);
            return new DayMarkerHolder(view);
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        int viewType = holder.getItemViewType();
        Object msg = getMessage(position);
        if (viewType == TYPE_MESSAGE || viewType == TYPE_MESSAGE_WITH_NEW_MESSAGE_MARKER) {
            ((MessageHolder) holder).bind((MessageItem) msg);
        } else if (viewType == TYPE_DAY_MARKER) {
            ((DayMarkerHolder) holder).bind((DayMarkerItem) msg);
        }
    }

    @Override
    public CharSequence getTextAt(int position) {
        Object msg = getMessage(position);
        if (msg instanceof MessageItem)
            return MessageBuilder.getInstance(mFragment.getContext())
                    .buildMessage(((MessageItem) msg).mMessage);
        else if (msg instanceof DayMarkerItem)
            return ((DayMarkerItem) msg).getMessageText(mFragment.getContext());
        return null;
    }

    @Override
    public int getItemCount() {
        return mPrependedMessages.size() + mMessages.size();
    }

    @Override
    public int getItemViewType(int position) {
        Object m = getMessage(position);
        if (m instanceof MessageItem) {
            if (((MessageItem) m).mMessageId.equals(mNewMessagesStart))
                return TYPE_MESSAGE_WITH_NEW_MESSAGE_MARKER;
            return TYPE_MESSAGE;
        }
        if (m instanceof DayMarkerItem)
            return TYPE_DAY_MARKER;
        return 0;
    }

    @Override
    public long getItemId(int position) {
        return position - mItemIdOffset;
    }

    @Override
    public int getItemPosition(long id) {
        return (int) (id + mItemIdOffset);
    }

    public class MessageHolder extends RecyclerView.ViewHolder {

        private TextView mText;
        private ViewGroup.LayoutParams mDefaultLayoutParams;

        public MessageHolder(View v) {
            super(v);
            mDefaultLayoutParams = v.getLayoutParams();
            mText = v.findViewById(R.id.chat_message);
            mText.setOnLongClickListener((View view) -> {
                if (mSelectListener != null)
                    mSelectListener.startLongPressSelect();
                return true;
            });
            mText.setMovementMethod(SelectableLinkMovementMethod.getInstance());
        }

        private ClickableSpan createNickClickSpan(String nick) {
            if (nick == null || nick.isEmpty())
                return null;
            return new LongClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    UserBottomSheetDialog dialog = new UserBottomSheetDialog(widget.getContext());
                    dialog.setConnection(mFragment.getConnectionInfo());
                    dialog.setSourceChannel(mFragment.getChannelName());
                    dialog.requestData(nick, mFragment.getConnectionInfo().getApiInstance());
                    Dialog shownDialog = dialog.show();
                    if (mFragment.getActivity() instanceof MainActivity)
                        ((MainActivity) mFragment.getActivity()).setFragmentDialog(shownDialog);
                }

                @Override
                public boolean onLongClick(@NonNull View widget) {
                    NicknameContextMenu.show(widget.getContext(), mFragment.getConnectionInfo(), nick,
                            mFragment.getChannelName());
                    return true;
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    // Preserve the existing nickname colour and avoid adding an underline.
                    ds.setUnderlineText(false);
                }
            };
        }

        public void bind(MessageItem item) {
            itemView.setVisibility(item.mHidden ? View.GONE : View.VISIBLE);
            if (item.mHidden)
                itemView.setLayoutParams(new RecyclerView.LayoutParams(0, 0));
            else if (itemView.getLayoutParams() != mDefaultLayoutParams)
                itemView.setLayoutParams(mDefaultLayoutParams);
            MessageInfo message = item.mMessage;
            if (mTypeface != null)
                mText.setTypeface(mTypeface);
            if (mFontSize != -1)
                mText.setTextSize(TypedValue.COMPLEX_UNIT_SP, mFontSize);

            MessageBuilder.NickClickSpanFactory nickClickSpanFactory = this::createNickClickSpan;
            if (NotificationManager.getInstance().shouldMessageUseMentionFormatting(mFragment.getConnectionInfo(), mFragment.getChannelName(), message))
                mText.setText(AlignToPointSpan.apply(mText, MessageBuilder.getInstance(mText.getContext())
                        .buildMessageWithMention(message, nickClickSpanFactory)));
            else
                mText.setText(AlignToPointSpan.apply(mText, MessageBuilder.getInstance(mText.getContext())
                        .buildMessage(message, nickClickSpanFactory)));

            if (mSelectListener != null) {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION)
                    mSelectListener.applySelectionTo(itemView, position);
            }
        }

    }

    public static class Item {
    }

    public static class MessageItem extends Item {

        MessageInfo mMessage;
        MessageId mMessageId;
        boolean mHidden;

        public MessageItem(MessageInfo message, MessageId msgId) {
            mMessage = message;
            mMessageId = msgId;
        }

    }

    public static class DayMarkerItem extends Item {

        int mDate;

        public DayMarkerItem(int date) {
            mDate = date;
        }

        public String getMessageText(Context ctx) {
            return DateUtils.formatDateTime(ctx, getDateIntMs(mDate), DateUtils.FORMAT_SHOW_DATE);
        }

    }

    public class DayMarkerHolder extends RecyclerView.ViewHolder {

        private TextView mText;

        public DayMarkerHolder(View itemView) {
            super(itemView);
            mText = itemView.findViewById(R.id.text);
        }

        public void bind(DayMarkerItem item) {
            if (mTypeface != null)
                mText.setTypeface(mTypeface);
            if (mFontSize != -1)
                mText.setTextSize(TypedValue.COMPLEX_UNIT_SP, mFontSize);

            mText.setText(item.getMessageText(mText.getContext()));
        }

    }


    private static final Calendar sDayIntCalendar = Calendar.getInstance();
    private static final int sDaysInYear = sDayIntCalendar.getMaximum(Calendar.DAY_OF_YEAR);

    private static int getDayInt(Date date) {
        sDayIntCalendar.setTime(date);
        return sDayIntCalendar.get(Calendar.YEAR) * (sDaysInYear + 1) +
                sDayIntCalendar.get(Calendar.DAY_OF_YEAR);
    }

    private static long getDateIntMs(int date) {
        sDayIntCalendar.setTimeInMillis(0);
        sDayIntCalendar.set(Calendar.YEAR, date / (sDaysInYear + 1));
        sDayIntCalendar.set(Calendar.DAY_OF_YEAR, date % (sDaysInYear + 1));
        return sDayIntCalendar.getTimeInMillis();
    }

}
