package io.mrarm.irc.chat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.mrarm.chatlib.ChannelInfoListener;
import io.mrarm.chatlib.ResponseCallback;
import io.mrarm.chatlib.StatusMessageListener;
import io.mrarm.chatlib.dto.ChannelInfo;
import io.mrarm.chatlib.dto.KickMessageInfo;
import io.mrarm.chatlib.dto.MessageFilterOptions;
import io.mrarm.chatlib.dto.MessageId;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageList;
import io.mrarm.chatlib.dto.MessageListAfterIdentifier;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.chatlib.dto.NickChangeMessageInfo;
import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.chatlib.dto.StatusMessageInfo;
import io.mrarm.chatlib.dto.StatusMessageList;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.ServerConnectionApi;
import io.mrarm.chatlib.message.MessageListener;
import io.mrarm.chatlib.message.MessageStorageApi;
import io.mrarm.irc.ChannelNotificationManager;
import io.mrarm.irc.MainActivity;
import io.mrarm.irc.NotificationManager;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.ServerConnectionManager;
import io.mrarm.irc.config.ChatSettings;
import io.mrarm.irc.config.MessageFormatSettings;
import io.mrarm.irc.config.UiSettingChangeCallback;
import io.mrarm.irc.util.LongPressSelectTouchListener;
import io.mrarm.irc.util.ScrollPosLinearLayoutManager;
import io.mrarm.irc.config.SettingsHelper;


public class ChatMessagesFragment extends Fragment implements StatusMessageListener,
        MessageListener, ChannelInfoListener, NotificationManager.UnreadMessageCountCallback {

    private static final String TAG = "ChatMessagesFragment";

    private static final String ARG_SERVER_UUID = "server_uuid";
    private static final String ARG_DISPLAY_STATUS = "display_status";
    private static final String ARG_CHANNEL_NAME = "channel";

    private static final int LOAD_MORE_BEFORE_INDEX = 10;
    private static final int MAX_LIVE_STATUS_MESSAGES = 200;
    private static final long OFFSCREEN_RELEASE_DELAY_MS = 60_000L;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private static final MessageFilterOptions sFilterJoinParts;

    private List<NickWithPrefix> mMembers = null;

    private ServerConnectionInfo mConnection;
    private String mChannelName;
    private String mChannelTopic;
    private MessageSenderInfo mChannelTopicSetBy;
    private Date mChannelTopicSetOn;
    private RecyclerView mRecyclerView;
    private ScrollPosLinearLayoutManager mLayoutManager;
    private ChatMessagesAdapter mAdapter;
    private ServerStatusMessagesAdapter mStatusAdapter;
    private List<StatusMessageInfo> mStatusMessages;
    private boolean mNeedsUnsubscribeChannelInfo = false;
    private boolean mNeedsUnsubscribeMessages = false;
    private boolean mNeedsUnsubscribeStatusMessages = false;
    private MessageListAfterIdentifier mLoadOlderIdentifier;
    private MessageListAfterIdentifier mLoadNewerIdentifier;
    private boolean mIsLoadingMore;
    private MessageFilterOptions mMessageFilterOptions;
    private View mUnreadCtr;
    private TextView mUnreadText;
    private View mUnreadDiscard;
    private View mMentionNavigation;
    private TextView mMentionPosition;
    private View mMentionPrevious;
    private View mMentionNext;
    private List<MessageId> mMentionIds = new ArrayList<>();
    private int mMentionIndex = -1;
    private MessageId mMentionRestoreMessage;
    private MessageId mPendingHighlightMessage;
    private long mUnreadCheckedFirst = -1;
    private long mUnreadCheckedLast = -1;
    private MessageId mUnreadCheckFor;
    private volatile boolean mIsResumed;
    private volatile boolean mMessagesChangedWhilePaused;
    private volatile boolean mStatusChangedWhilePaused;
    private boolean mMessagesReleased;
    private final Runnable mReleaseOffscreenMessages = () -> {
        if (mIsResumed)
            return;
        if (mAdapter != null) {
            mAdapter.releaseMessages();
            mMessagesReleased = true;
        }
    };

    static {
        sFilterJoinParts = new MessageFilterOptions();
        sFilterJoinParts.excludeMessageTypes = new ArrayList<>();
        sFilterJoinParts.excludeMessageTypes.add(MessageInfo.MessageType.JOIN);
        sFilterJoinParts.excludeMessageTypes.add(MessageInfo.MessageType.PART);
        sFilterJoinParts.excludeMessageTypes.add(MessageInfo.MessageType.QUIT);
    }

    public ChatMessagesFragment() {
    }

    public static ChatMessagesFragment newInstance(ServerConnectionInfo server,
                                                   String channelName) {
        ChatMessagesFragment fragment = new ChatMessagesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SERVER_UUID, server.getUUID().toString());
        if (channelName != null)
            args.putString(ARG_CHANNEL_NAME, channelName);
        fragment.setArguments(args);
        return fragment;
    }

    public static ChatMessagesFragment newStatusInstance(ServerConnectionInfo server) {
        ChatMessagesFragment fragment = new ChatMessagesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SERVER_UUID, server.getUUID().toString());
        args.putBoolean(ARG_DISPLAY_STATUS, true);
        fragment.setArguments(args);
        return fragment;
    }

    private MessageFilterOptions getFilterOptions() {
        return mMessageFilterOptions;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UUID connectionUUID = UUID.fromString(getArguments().getString(ARG_SERVER_UUID));
        ServerConnectionInfo connectionInfo = ServerConnectionManager.getInstance(getContext())
                .getConnection(connectionUUID);
        mConnection = connectionInfo;
        mChannelName = getArguments().getString(ARG_CHANNEL_NAME);

        if (mChannelName != null) {
            mAdapter = new ChatMessagesAdapter(this, new ArrayList<>(), new ArrayList<>());
            mAdapter.setMessageFont(ChatSettings.getFont(), ChatSettings.getFontSize());

            Log.i(TAG, "Request message list for: " + mChannelName);
            connectionInfo.getApiInstance().getChannelInfo(mChannelName,
                    (ChannelInfo channelInfo) -> {
                        Log.i(TAG, "Got channel info " + mChannelName);
                        mChannelTopic = channelInfo.getTopic();
                        mChannelTopicSetBy = channelInfo.getTopicSetBy();
                        mChannelTopicSetOn = channelInfo.getTopicSetOn();
                        onMemberListChanged(channelInfo.getMembers());
                    }, null);

            connectionInfo.getApiInstance().subscribeChannelInfo(mChannelName, this, null, null);
            mNeedsUnsubscribeChannelInfo = true;

            String msgIdStr = ((ChatFragment) getParentFragment()).getAndClearMessageJump(mChannelName);
            MessageId msgId = null;
            if (msgIdStr != null)
                msgId = mConnection.getApiInstance().getMessageStorageApi().getMessageIdParser().parse(msgIdStr);
            reloadMessages(msgId);
        } else if (getArguments().getBoolean(ARG_DISPLAY_STATUS)) {
            mStatusAdapter = new ServerStatusMessagesAdapter(mConnection, new StatusMessageList(new ArrayList<>()));
            mStatusAdapter.setMessageFont(ChatSettings.getFont(), ChatSettings.getFontSize());

            Log.i(TAG, "Request status message list");
            connectionInfo.getApiInstance().getStatusMessages(100, null,
                    (StatusMessageList messages) -> {
                        Log.i(TAG, "Got server status message list: " +
                                messages.getMessages().size() + " messages");
                        mStatusMessages = messages.getMessages();
                        mNeedsUnsubscribeStatusMessages = true;
                        updateMessageList(() -> {
                            mStatusAdapter.setMessages(messages);
                            if (mRecyclerView != null)
                                mRecyclerView.scrollToPosition(mStatusAdapter.getItemCount() - 1);
                        });

                        connectionInfo.getApiInstance().subscribeStatusMessages(ChatMessagesFragment.this, null, null);
                    }, null);
        }

        SettingsHelper.registerCallbacks(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mMainHandler.removeCallbacks(mReleaseOffscreenMessages);

        SettingsHelper.unregisterCallbacks(this);

        if (mNeedsUnsubscribeChannelInfo)
            mConnection.getApiInstance().unsubscribeChannelInfo(getArguments().getString(ARG_CHANNEL_NAME), ChatMessagesFragment.this, null, null);
        if (mNeedsUnsubscribeMessages)
            mConnection.getApiInstance().getMessageStorageApi().unsubscribeChannelMessages(getArguments().getString(ARG_CHANNEL_NAME), ChatMessagesFragment.this, null, null);
        if (mNeedsUnsubscribeStatusMessages)
            mConnection.getApiInstance().unsubscribeStatusMessages(ChatMessagesFragment.this, null, null);

        mConnection.getNotificationManager().removeUnreadMessageCountCallback(this);

        hideMessagesActionMenu();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.chat_messages_fragment, container, false);
        synchronized (this) {
            mRecyclerView = rootView.findViewById(R.id.messages);
            mUnreadCtr = rootView.findViewById(R.id.unread_counter_ctr);
            mUnreadText = rootView.findViewById(R.id.unread_counter);
            mUnreadDiscard = rootView.findViewById(R.id.unread_counter_discard);
            mMentionNavigation = rootView.findViewById(R.id.mention_navigation);
            mMentionPosition = rootView.findViewById(R.id.mention_position);
            mMentionPrevious = rootView.findViewById(R.id.mention_previous);
            mMentionNext = rootView.findViewById(R.id.mention_next);
        }
        mLayoutManager = new ScrollPosLinearLayoutManager(getContext());
        mLayoutManager.setStackFromEnd(true);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (mAdapter == null)
                    return;
                checkForUnreadMessages();
                updateReadPosition();
                int firstVisible = mLayoutManager.findFirstVisibleItemPosition();
                if (firstVisible >= 0 && firstVisible < LOAD_MORE_BEFORE_INDEX) {
                    if (mIsLoadingMore || mLoadOlderIdentifier == null || !mAdapter.hasMessages())
                        return;
                    Log.i(TAG, "Load more (older): " + mChannelName);
                    mIsLoadingMore = true;
                    mConnection.getApiInstance().getMessageStorageApi().getMessages(mChannelName,
                            100, getFilterOptions(), mLoadOlderIdentifier,
                            (MessageList messages) -> {
                                updateMessageList(() -> {
                                    mAdapter.addMessagesToTop(messages.getMessages(),
                                            messages.getMessageIds());
                                    mLoadOlderIdentifier = messages.getOlder();
                                    mIsLoadingMore = false;
                                });
                            }, null);
                }
                int lastVisible = mLayoutManager.findLastVisibleItemPosition();
                if (lastVisible <= mAdapter.getItemCount() &&
                        lastVisible > mAdapter.getItemCount() - LOAD_MORE_BEFORE_INDEX) {
                    if (mIsLoadingMore || mLoadNewerIdentifier == null || !mAdapter.hasMessages())
                        return;
                    Log.i(TAG, "Load more (newer): " + mChannelName);
                    mIsLoadingMore = true;
                    mConnection.getApiInstance().getMessageStorageApi().getMessages(mChannelName,
                            100, getFilterOptions(), mLoadNewerIdentifier,
                            (MessageList messages) -> {
                                updateMessageList(() -> {
                                    mAdapter.addMessagesToBottom(messages.getMessages(),
                                            messages.getMessageIds());
                                    mLoadNewerIdentifier = messages.getNewer();
                                    mIsLoadingMore = false;
                                });
                            }, null);
                }
            }
        });

        mUnreadCtr.setOnClickListener((v) -> {
            ChannelNotificationManager mgr = mConnection.getNotificationManager().getChannelManager(mChannelName, true);
            if (mgr.getMentionCount() > 0) {
                startMentionNavigation(true);
                return;
            }
            if (mgr.getUnreadMessageCount() > 99) {
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.unread_backlog_title)
                        .setItems(new CharSequence[] {
                                getString(R.string.unread_go_first),
                                getString(R.string.unread_go_latest)
                        }, (dialog, which) -> {
                            if (which == 0)
                                goToFirstUnread();
                            else
                                goToLatestAndMarkRead();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return;
            }
            goToFirstUnread();
        });
        mUnreadDiscard.setOnClickListener((v) -> markAsRead());
        mMentionPrevious.setOnClickListener(v -> showMention(mMentionIndex - 1));
        mMentionNext.setOnClickListener(v -> showMention(mMentionIndex + 1));
        rootView.findViewById(R.id.mention_close).setOnClickListener(v -> closeMentionNavigation(true));

        if (mAdapter != null) {
            mRecyclerView.setAdapter(mAdapter);

            LongPressSelectTouchListener selectTouchListener =
                    new LongPressSelectTouchListener(mRecyclerView);
            mAdapter.setMultiSelectListener(selectTouchListener);
            mRecyclerView.addOnItemTouchListener(selectTouchListener);

            if (!ChatSettings.shouldUseOnlyMultiSelectMode()) {
                ChatSelectTouchListener newSelectTouchListener =
                        new ChatSelectTouchListener(mRecyclerView);
                newSelectTouchListener.setMultiSelectListener(selectTouchListener);
                newSelectTouchListener.setActionModeStateCallback((android.view.ActionMode actionMode,
                                                                boolean b) -> {
                    if (actionMode.getType() == android.view.ActionMode.TYPE_FLOATING)
                        return;
                    ((ChatFragment) getParentFragment()).setTabsHidden(b);
                });
                mAdapter.setSelectListener(newSelectTouchListener);
                mRecyclerView.addOnItemTouchListener(newSelectTouchListener);
            }
        } else if (mStatusAdapter != null) {
            mRecyclerView.setAdapter(mStatusAdapter);
        }

        if (mIsResumed)
            ((ChatFragment) getParentFragment()).getSendMessageHelper()
                    .setCurrentChannel(mChannelName);

        updateUnreadCounter();

        return rootView;
    }

    private void goToFirstUnread() {
        ChannelNotificationManager mgr = mConnection.getNotificationManager()
                .getChannelManager(mChannelName, true);
        MessageId msgId = mgr.getFirstUnreadMessage();
        if (msgId == null)
            return;
        int index = mAdapter.findMessageWithId(msgId);
        if (index != -1)
            ((LinearLayoutManager) mRecyclerView.getLayoutManager())
                    .scrollToPositionWithOffset(index, 0);
        else
            reloadMessages(msgId);
    }

    public void markAsRead() {
        if (mConnection == null || mChannelName == null)
            return;
        ChannelNotificationManager mgr = mConnection.getNotificationManager()
                .getChannelManager(mChannelName, true);
        mgr.clearUnreadMessages();
        if (mAdapter != null)
            mAdapter.setNewMessagesStart(null);
        updateUnreadCounter();
    }

    public void goToLatestAndMarkRead() {
        markAsRead();
        if (mLoadNewerIdentifier != null)
            reloadMessages(null);
        else if (mRecyclerView != null && mAdapter != null && mAdapter.getItemCount() > 0)
            mRecyclerView.scrollToPosition(mAdapter.getItemCount() - 1);
    }

    public void startMentionNavigation() {
        startMentionNavigation(false);
    }

    private void startMentionNavigation(boolean oldestFirst) {
        if (mConnection == null || mChannelName == null || mAdapter == null)
            return;
        ChannelNotificationManager manager = mConnection.getNotificationManager()
                .getChannelManager(mChannelName, true);
        List<String> storedIds = manager.getMentionMessageIds();
        mMentionIds = new ArrayList<>();
        for (String id : storedIds) {
            try {
                mMentionIds.add(mConnection.getMessageIdParser().parse(id));
            } catch (RuntimeException ignored) { }
        }
        if (mMentionIds.isEmpty()) {
            Toast.makeText(getContext(), R.string.mention_none, Toast.LENGTH_SHORT).show();
            return;
        }
        int firstVisible = mLayoutManager.findFirstVisibleItemPosition();
        ChatMessagesAdapter.Item item = firstVisible >= 0 ? mAdapter.getMessage(firstVisible) : null;
        mMentionRestoreMessage = item instanceof ChatMessagesAdapter.MessageItem ?
                ((ChatMessagesAdapter.MessageItem) item).mMessageId : null;
        mMentionNavigation.setVisibility(View.VISIBLE);
        showMention(oldestFirst ? 0 : mMentionIds.size() - 1);
    }

    private void showMention(int index) {
        if (index < 0 || index >= mMentionIds.size())
            return;
        mMentionIndex = index;
        mMentionPosition.setText(getString(R.string.mention_position, index + 1, mMentionIds.size()));
        mMentionPrevious.setEnabled(index > 0);
        mMentionNext.setEnabled(index < mMentionIds.size() - 1);
        MessageId id = mMentionIds.get(index);
        mConnection.getNotificationManager().getChannelManager(mChannelName, true)
                .markMentionReviewed(id.toString());
        updateUnreadCounter();
        jumpToMessage(id, true);
    }

    private void jumpToMessage(MessageId id, boolean highlight) {
        if (id == null || mAdapter == null || mRecyclerView == null)
            return;
        int index = mAdapter.findMessageWithId(id);
        if (index == -1) {
            mPendingHighlightMessage = highlight ? id : null;
            reloadMessages(id);
            return;
        }
        ((LinearLayoutManager) mRecyclerView.getLayoutManager()).scrollToPositionWithOffset(index,
                Math.max(0, mRecyclerView.getHeight() / 3));
        if (highlight) {
            mRecyclerView.post(() -> {
                int current = mAdapter.findMessageWithId(id);
                if (current != -1)
                    mAdapter.flashMessage(mRecyclerView, current);
            });
        }
    }

    private void closeMentionNavigation(boolean restorePosition) {
        if (mMentionNavigation != null)
            mMentionNavigation.setVisibility(View.GONE);
        MessageId restore = mMentionRestoreMessage;
        mMentionRestoreMessage = null;
        mMentionIds.clear();
        mMentionIndex = -1;
        if (restorePosition && restore != null)
            jumpToMessage(restore, false);
    }

    public void markAllMentionsReviewed() {
        if (mConnection == null || mChannelName == null)
            return;
        mConnection.getNotificationManager().getChannelManager(mChannelName, true)
                .markAllMentionsReviewed();
        updateUnreadCounter();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        synchronized (this) {
            mRecyclerView = null;
        }
        if (mAdapter != null) {
            mAdapter.setSelectListener(null);
        }
    }

    private void reloadMessages(MessageId nearMessage) {
        if (mConnection.shouldHideJoinPartMessages())
            mMessageFilterOptions = sFilterJoinParts;
        else
            mMessageFilterOptions = null;
        mUnreadCheckedFirst = -1;
        mUnreadCheckedLast = -1;
        mAdapter.setNewMessagesStart(mConnection.getNotificationManager()
                .getChannelManager(mChannelName, true).getFirstUnreadMessage());
        ResponseCallback<MessageList> cb = (MessageList messages) -> {
            Log.i(TAG, "Got message list for " + mChannelName + ": " +
                    messages.getMessages().size() + " messages");
            updateMessageList(() -> {
                mAdapter.setMessages(messages.getMessages(), messages.getMessageIds());
                if (mRecyclerView != null) {
                    int nearIndex = nearMessage == null ? -1 : mAdapter.findMessageWithId(nearMessage);
                    if (nearIndex >= 0) {
                        ((LinearLayoutManager) mRecyclerView.getLayoutManager())
                                .scrollToPositionWithOffset(nearIndex, 0);
                        if (mPendingHighlightMessage != null &&
                                mPendingHighlightMessage.equals(nearMessage)) {
                            mRecyclerView.post(() -> {
                                int current = mAdapter.findMessageWithId(nearMessage);
                                if (current != -1)
                                    mAdapter.flashMessage(mRecyclerView, current);
                            });
                            mPendingHighlightMessage = null;
                        }
                    } else {
                        mRecyclerView.scrollToPosition(mAdapter.getItemCount() - 1);
                        if (nearMessage != null && mPendingHighlightMessage != null) {
                            Toast.makeText(getContext(), R.string.mention_unavailable,
                                    Toast.LENGTH_SHORT).show();
                            mPendingHighlightMessage = null;
                        }
                    }
                    mRecyclerView.post(this::updateReadPosition);
                }
                mLoadOlderIdentifier = messages.getOlder();
            });

            if (!mNeedsUnsubscribeMessages) {
                mConnection.getApiInstance().getMessageStorageApi().subscribeChannelMessages(mChannelName, ChatMessagesFragment.this, null, null);
                mNeedsUnsubscribeMessages = true;
            }
        };
        MessageStorageApi storage = mConnection.getApiInstance().getMessageStorageApi();
        if (nearMessage != null) {
            storage.getMessagesNear(mChannelName, nearMessage,
                    getFilterOptions(), (MessageList messages) -> {
                        cb.onResponse(messages);
                        updateMessageList(() -> {
                            mLoadNewerIdentifier = messages.getNewer();
                        });
                    }, null);
        } else {
            mConnection.getApiInstance().getMessageStorageApi().getMessages(mChannelName, 100,
                    getFilterOptions(), null, cb, null);
        }
    }

    private void updateUnreadCounter() {
        if (mConnection == null || mRecyclerView == null)
            return;
        ChannelNotificationManager mgr = mConnection.getNotificationManager().getChannelManager(mChannelName, true);
        int unread = mgr.getUnreadMessageCount();
        int mentions = mgr.getMentionCount();
        MessageId unreadMsg = mgr.getFirstUnreadMessage();
        if (unreadMsg == null && unread > 0) {
            unread = 0;
            mgr.clearUnreadMessages();
        }
        if (unread > 0) {
            int index = mAdapter.findMessageWithId(unreadMsg);
            View v = mRecyclerView.getLayoutManager().findViewByPosition(index);
            if (v != null && mRecyclerView.getLayoutManager().isViewPartiallyVisible(v, true, true)) {
                unread = 0;
                mgr.clearUnreadMessages();
            }
            mAdapter.setNewMessagesStart(unreadMsg);
        }
        mUnreadCtr.setVisibility(View.GONE);
        if (unread > 0 || mentions > 0) {
            if (mUnreadCheckFor == null || !mUnreadCheckFor.equals(unreadMsg)) {
                mUnreadCheckFor = unreadMsg;
                mUnreadCheckedFirst = -1;
                mUnreadCheckedLast = -1;
            }
            mUnreadCtr.setVisibility(View.VISIBLE);
            String unreadText = unread > 99 ? getString(R.string.unread_99_plus) :
                    getResources().getQuantityString(R.plurals.unread_message_counter, unread, unread);
            if (mentions > 0 && unread > 0) {
                String mentionText = getResources().getQuantityString(R.plurals.mention_counter,
                        mentions, mentions);
                mUnreadText.setText(getString(R.string.unread_counter_with_mentions,
                        unreadText, mentionText));
            } else if (mentions > 0) {
                mUnreadText.setText("@" + mentions);
            } else {
                mUnreadText.setText(unreadText);
            }
        }
    }

    public String getFirstVisibleMessageId() {
        if (mLayoutManager == null || mAdapter == null)
            return null;
        int position = mLayoutManager.findFirstVisibleItemPosition();
        ChatMessagesAdapter.Item item = position < 0 ? null : mAdapter.getMessage(position);
        return item instanceof ChatMessagesAdapter.MessageItem ?
                ((ChatMessagesAdapter.MessageItem) item).mMessageId.toString() : null;
    }

    private void updateReadPosition() {
        if (mConnection == null || mChannelName == null || mRecyclerView == null ||
                mAdapter == null || mLayoutManager == null)
            return;
        int last = mLayoutManager.findLastCompletelyVisibleItemPosition();
        boolean atBottom = mLoadNewerIdentifier == null && mAdapter.getItemCount() > 0 &&
                last >= mAdapter.getItemCount() - 2;
        ChannelNotificationManager manager = mConnection.getNotificationManager()
                .getChannelManager(mChannelName, true);
        boolean visible = mIsResumed;
        manager.setAtBottom(visible && atBottom);
        if (visible && atBottom && manager.getUnreadMessageCount() > 0) {
            manager.clearUnreadMessages();
            mAdapter.setNewMessagesStart(null);
            // Reading ordinary messages must not hide mentions that still need review.
            updateUnreadCounter();
        }
    }

    private void checkForUnreadMessages() {
        if (mUnreadCtr.getVisibility() == View.GONE)
            return;
        LinearLayoutManager llm = (LinearLayoutManager) mRecyclerView.getLayoutManager();
        int firstPos = llm.findFirstCompletelyVisibleItemPosition();
        int lastPos = llm.findLastCompletelyVisibleItemPosition();
        long firstId = firstPos != RecyclerView.NO_POSITION ? mAdapter.getItemId(firstPos) : -1;
        long lastId = lastPos != RecyclerView.NO_POSITION ? mAdapter.getItemId(lastPos) : -1;
        boolean found = false;
        if (mUnreadCheckedFirst == -1 && firstId != -1) {
            mUnreadCheckedFirst = firstId;
            mUnreadCheckedLast = firstId;
            found = checkItemForUnread(
                    mAdapter.getMessage(mAdapter.getItemPosition(firstId)), mUnreadCheckFor);
        }
        while (firstId != -1 && firstId < mUnreadCheckedFirst) {
            found |= checkItemForUnread(mAdapter.getMessage(
                    mAdapter.getItemPosition(mUnreadCheckedFirst)), mUnreadCheckFor);
            if (found)
                break;
            --mUnreadCheckedFirst;
        }
        while (lastId != -1 && lastId > mUnreadCheckedLast) {
            found |= checkItemForUnread(mAdapter.getMessage(
                    mAdapter.getItemPosition(mUnreadCheckedLast)), mUnreadCheckFor);
            if (found)
                break;
            ++mUnreadCheckedLast;
        }
        if (found) {
            ChannelNotificationManager mgr = mConnection.getNotificationManager()
                    .getChannelManager(mChannelName, true);
            mgr.clearUnreadMessages();
            // The unread boundary was reached, but reviewed-state is independent.
            updateUnreadCounter();
            mUnreadCheckedFirst = -1;
            mUnreadCheckedLast = -1;
        }
    }

    private boolean checkItemForUnread(ChatMessagesAdapter.Item item, MessageId lookingFor) {
        if (item instanceof ChatMessagesAdapter.MessageItem) {
            return ((ChatMessagesAdapter.MessageItem) item).mMessageId.equals(lookingFor);
        }
        return false;
    }

    @Override
    public void onUnreadMessageCountChanged(ServerConnectionInfo info, String channel, int messageCount, int oldMessageCount) {
        if (channel.equals(mChannelName)) {
            updateMessageList(this::updateUnreadCounter);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsResumed = true;
        mMainHandler.removeCallbacks(mReleaseOffscreenMessages);
        if (mMessagesReleased && mConnection != null && mChannelName != null) {
            mMessagesReleased = false;
            mMessagesChangedWhilePaused = false;
            reloadMessages(null);
        }
        if (mMessagesChangedWhilePaused && mChannelName != null) {
            mMessagesChangedWhilePaused = false;
            reloadMessages(null);
        }
        if (mStatusChangedWhilePaused && mStatusAdapter != null) {
            mStatusChangedWhilePaused = false;
            mConnection.getApiInstance().getStatusMessages(100, null,
                    (StatusMessageList messages) -> updateMessageList(() -> {
                        mStatusMessages = messages.getMessages();
                        mStatusAdapter.setMessages(messages);
                        if (mRecyclerView != null)
                            mRecyclerView.scrollToPosition(mStatusAdapter.getItemCount() - 1);
                    }), null);
        }
        if (getParentFragment() != null) {
            updateParentCurrentChannel();
            ((ChatFragment) getParentFragment()).getSendMessageHelper()
                    .setCurrentChannel(mChannelName);
        }
        if (mConnection != null) {
            mConnection.getNotificationManager().getChannelManager(mChannelName, true).setOpened(getContext(), true);
            mConnection.getNotificationManager().addUnreadMessageCountCallback(this);
            updateUnreadCounter();
            if (mRecyclerView != null)
                mRecyclerView.post(this::updateReadPosition);
            requestMemberRefreshIfNeeded();
        }
    }

    @Override
    public void onPause() {
        mIsResumed = false;
        mMainHandler.removeCallbacks(mReleaseOffscreenMessages);
        if (mAdapter != null)
            mMainHandler.postDelayed(mReleaseOffscreenMessages, OFFSCREEN_RELEASE_DELAY_MS);
        hideMessagesActionMenu();
        super.onPause();
        MainActivity activity = (MainActivity) getActivity();
        if (mConnection != null && (activity == null || !activity.isAppExiting()))
            mConnection.getNotificationManager().getChannelManager(mChannelName, true).setOpened(getContext(), false);
        if (mConnection != null && mChannelName != null)
            mConnection.getNotificationManager().getChannelManager(mChannelName, true)
                    .setAtBottom(false);
        if (mConnection != null)
            mConnection.getNotificationManager().removeUnreadMessageCountCallback(this);
        if (mUnreadCtr != null)
            mUnreadCtr.setVisibility(View.GONE);
    }

    @UiSettingChangeCallback(keys = {
            ChatSettings.PREF_FONT,
            ChatSettings.PREF_FONT_SIZE,
            // it's enough to only register to the last format preference, as all preferences are always rewritten
            MessageFormatSettings.PREF_MESSAGE_FORMAT_EVENT_HOSTNAME
    })
    private void onSettingChanged() {
        if (mAdapter != null) {
            mAdapter.setMessageFont(ChatSettings.getFont(), ChatSettings.getFontSize());
            mAdapter.notifyDataSetChanged();
        }
        if (mStatusAdapter != null) {
            mStatusAdapter.setMessageFont(ChatSettings.getFont(), ChatSettings.getFontSize());
            mStatusAdapter.notifyDataSetChanged();
        }
    }

    private void updateParentCurrentChannel() {
        mMainHandler.post(() -> {
            Fragment parent = getParentFragment();
            if (!isAdded() || !(parent instanceof ChatFragment))
                return;
            ((ChatFragment) parent).setCurrentChannelInfo(mChannelTopic,
                    mChannelTopicSetBy != null ? mChannelTopicSetBy.getNick() : null,
                    mChannelTopicSetOn, getMergedMembers());
        });
    }

    private void updateMessageList(Runnable r) {
        // Chatlib callbacks may arrive from its network thread. Keep every adapter and view
        // mutation on Android's main thread, including while the Fragment view is being rebuilt.
        mMainHandler.post(() -> {
            if (!isAdded())
                return;
            r.run();
        });
    }

    public ServerConnectionInfo getConnectionInfo() {
        return mConnection;
    }

    public String getChannelName() {
        return mChannelName;
    }

    public boolean isServerStatus() {
        return mStatusAdapter != null;
    }

    private void scrollToBottom() {
        int i = Math.max(mLayoutManager.findLastVisibleItemPosition(), mLayoutManager.getPendingScrollPosition());
        int count = mAdapter == null ? mStatusAdapter.getItemCount() : mAdapter.getItemCount();
        if (i >= count - 2)
            mRecyclerView.scrollToPosition(count - 1);
    }

    @Override
    public void onMessage(String channel, MessageInfo messageInfo, MessageId messageId) {
        trackVisibleMember(messageInfo);
        // The storage service still records the message while this screen is in the background.
        // Avoid building an unbounded queue of RecyclerView work; reload a bounded window later.
        if (!mIsResumed || mRecyclerView == null) {
            mMessagesChangedWhilePaused = true;
            return;
        }
        updateMessageList(() -> {
            if (!mIsResumed || mRecyclerView == null) {
                mMessagesChangedWhilePaused = true;
                return;
            }
            if (mLoadNewerIdentifier != null)
                return;
            MessageFilterOptions opt = getFilterOptions();
            if (opt != null) {
                if (opt.restrictToMessageTypes != null &&
                        !opt.restrictToMessageTypes.contains(messageInfo.getType()))
                    return;
                if (opt.excludeMessageTypes != null &&
                        opt.excludeMessageTypes.contains(messageInfo.getType()))
                    return;
            }

            mAdapter.appendMessage(messageInfo, messageId);
            if (mRecyclerView != null)
                scrollToBottom();
        });
    }

    @Override
    public void onStatusMessage(StatusMessageInfo statusMessageInfo) {
        if (!mIsResumed || mRecyclerView == null) {
            mStatusChangedWhilePaused = true;
            return;
        }
        updateMessageList(() -> {
            if (!mIsResumed || mRecyclerView == null) {
                mStatusChangedWhilePaused = true;
                return;
            }
            mStatusMessages.add(statusMessageInfo);
            if (mStatusMessages.size() > MAX_LIVE_STATUS_MESSAGES) {
                mStatusMessages.remove(0);
                mStatusAdapter.notifyDataSetChanged();
            } else {
                mStatusAdapter.notifyItemInserted(mStatusMessages.size() - 1);
            }
            if (mRecyclerView != null)
                scrollToBottom();
        });
    }

    @Override
    public void onMemberListChanged(List<NickWithPrefix> list) {
        List<NickWithPrefix> sorted = list != null ? new ArrayList<>(list) : new ArrayList<>();
        sortMembers(sorted);
        this.mMembers = sorted;
        if (mIsResumed)
            updateParentCurrentChannel();
    }

    private void sortMembers(List<NickWithPrefix> list) {
        Collections.sort(list, (NickWithPrefix left, NickWithPrefix right) -> {
            if (left.getNickPrefixes() != null && right.getNickPrefixes() != null) {
                char leftPrefix = left.getNickPrefixes().get(0);
                char rightPrefix = right.getNickPrefixes().get(0);
                for (char c : ((ServerConnectionApi) mConnection.getApiInstance())
                        .getServerConnectionData().getSupportList().getSupportedNickPrefixes()) {
                    if (leftPrefix == c && rightPrefix != c)
                        return -1;
                    if (rightPrefix == c && leftPrefix != c)
                        return 1;
                }
            } else if (left.getNickPrefixes() != null || right.getNickPrefixes() != null)
                return left.getNickPrefixes() != null ? -1 : 1;
            return left.getNick().compareToIgnoreCase(right.getNick());
        });
    }

    private List<NickWithPrefix> getMergedMembers() {
        Map<String, NickWithPrefix> merged = new LinkedHashMap<>();
        if (mMembers != null) {
            for (NickWithPrefix member : mMembers) {
                if (member != null && member.getNick() != null)
                    merged.put(member.getNick().toLowerCase(Locale.ROOT), member);
            }
        }
        if (mConnection != null && mChannelName != null) {
            for (String nick : mConnection.getChatUIData().getOrCreateChannelData(mChannelName)
                    .getObservedNicks()) {
                String key = nick.toLowerCase(Locale.ROOT);
                if (!merged.containsKey(key))
                    merged.put(key, new NickWithPrefix(nick, null));
            }
        }
        List<NickWithPrefix> result = new ArrayList<>(merged.values());
        sortMembers(result);
        return result;
    }

    private void trackVisibleMember(MessageInfo message) {
        if (message == null || mConnection == null || mChannelName == null)
            return;
        ChannelUIData data = mConnection.getChatUIData().getOrCreateChannelData(mChannelName);
        MessageSenderInfo sender = message.getSender();
        String nick = sender != null ? sender.getNick() : null;
        boolean changed = false;
        switch (message.getType()) {
            case NORMAL:
            case NOTICE:
            case ME:
            case JOIN:
                changed = data.observeNick(nick);
                break;
            case PART:
            case QUIT:
                changed = data.forgetNick(nick);
                break;
            case NICK_CHANGE:
                changed = data.renameNick(nick, ((NickChangeMessageInfo) message).getNewNick());
                break;
            case KICK:
                changed = data.forgetNick(((KickMessageInfo) message).getKickedNick());
                break;
            default:
                break;
        }
        if (changed && mIsResumed)
            updateParentCurrentChannel();
    }

    private void requestMemberRefreshIfNeeded() {
        if (mConnection == null || mChannelName == null || !mConnection.isConnected() ||
                !(mConnection.getApiInstance() instanceof IRCConnection))
            return;
        ServerConnectionApi api = (ServerConnectionApi) mConnection.getApiInstance();
        if (mChannelName.isEmpty() || !api.getServerConnectionData().getSupportList()
                .getSupportedChannelTypes().contains(mChannelName.charAt(0)))
            return;
        ChannelUIData data = mConnection.getChatUIData().getOrCreateChannelData(mChannelName);
        if (!data.shouldRefreshMembers(System.currentTimeMillis(), 60_000L))
            return;
        ((IRCConnection) mConnection.getApiInstance()).sendCommandRaw(
                "NAMES " + mChannelName, null, null);
    }

    @Override
    public void onTopicChanged(String topic, MessageSenderInfo topicSetBy, Date topicSetOn) {
        mChannelTopic = topic;
        mChannelTopicSetBy = topicSetBy;
        mChannelTopicSetOn = topicSetOn;
        if (mIsResumed)
            updateParentCurrentChannel();
    }

    public void showMessagesActionMenu() {
        if (mMessagesActionModeCallback == null)
            mMessagesActionModeCallback = new MessagesActionModeCallback();
        if (mMessagesActionModeCallback.mActionMode == null)
            mMessagesActionModeCallback.mActionMode = ((MainActivity) getActivity()).startSupportActionMode(mMessagesActionModeCallback);
    }

    public void hideMessagesActionMenu() {
        if (mMessagesActionModeCallback != null && mMessagesActionModeCallback.mActionMode != null) {
            mMessagesActionModeCallback.mActionMode.finish();
            mMessagesActionModeCallback.mActionMode = null;
        }
    }

    public void copySelectedMessages() {
        CharSequence messages = mAdapter.getSelectedMessages();
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("IRC Messages", messages));
    }

    public void shareSelectedMessages() {
        CharSequence messages = mAdapter.getSelectedMessages();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, messages);
        intent.setType("text/plain");
        mRecyclerView.getContext().startActivity(Intent.createChooser(intent,
                getString(R.string.message_share_title)));
    }

    public void deleteSelectedMessages() {
        List<MessageId> msgIds = mAdapter.getSelectedMessageIds();
        for (Long l : mAdapter.getSelectedItems()) {
            ChatMessagesAdapter.Item i = mAdapter.getMessage(mAdapter.getItemPosition(l));
            if (i instanceof ChatMessagesAdapter.MessageItem)
                ((ChatMessagesAdapter.MessageItem) i).mHidden = true;
        }
        mAdapter.notifyDataSetChanged();
        mConnection.getApiInstance().getMessageStorageApi().deleteMessages(mChannelName, msgIds,
                null, null);
    }


    private MessagesActionModeCallback mMessagesActionModeCallback;

    private class MessagesActionModeCallback implements ActionMode.Callback {

        public ActionMode mActionMode;

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.menu_context_messages_full, menu);
            ((ChatFragment) getParentFragment()).setTabsHidden(true);
            mActionMode = mode;
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_copy:
                    copySelectedMessages();
                    mode.finish();
                    return true;
                case R.id.action_share:
                    shareSelectedMessages();
                    mode.finish();
                    return true;
                case R.id.action_delete: {
                    int cnt = mAdapter.getSelectedItems().size();
                    new AlertDialog.Builder(getContext())
                            .setTitle(R.string.action_delete_confirm_title)
                            .setMessage(getResources().getQuantityString(R.plurals.message_delete_confirm, cnt, cnt) + "\n\n" + getResources().getString(R.string.message_delete_confirm_note))
                            .setPositiveButton(R.string.action_delete, (di, w) -> {
                                deleteSelectedMessages();
                                mode.finish();
                            })
                            .setNegativeButton(R.string.action_cancel, null)
                            .show();
                    return true;
                }
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            ((ChatFragment) getParentFragment()).setTabsHidden(false);
            mAdapter.clearSelection();
            mActionMode = null;
        }

    };

}
