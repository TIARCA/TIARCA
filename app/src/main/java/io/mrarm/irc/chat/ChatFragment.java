package io.mrarm.irc.chat;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import androidx.core.widget.ImageViewCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.irc.ChannelNotificationManager;
import io.mrarm.irc.DirectShareManager;
import io.mrarm.irc.MainActivity;
import io.mrarm.irc.NotificationManager;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.ServerConnectionManager;
import io.mrarm.irc.config.ChatSettings;
import io.mrarm.irc.config.NickAutocompleteSettings;
import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.config.UiSettingChangeCallback;

public class ChatFragment extends Fragment implements
        ServerConnectionInfo.ChannelListChangeListener,
        ServerConnectionInfo.InfoChangeListener,
        NotificationManager.UnreadMessageCountCallback {

    public static final String ARG_SERVER_UUID = "server_uuid";
    public static final String ARG_CHANNEL_NAME = "channel";
    public static final String ARG_MESSAGE_ID = "message_id";
    public static final String ARG_SEND_MESSAGE_TEXT = "message_text";

    private ServerConnectionInfo mConnectionInfo;

    private AppBarLayout mAppBar;
    private TabLayout mTabLayout;
    private ChatPagerAdapter mSectionsPagerAdapter;
    private ViewPager2 mViewPager;
    private TabLayoutMediator mTabLayoutMediator;
    private ViewPager2.OnPageChangeCallback mPageChangeCallback;
    private RecyclerView.AdapterDataObserver mPagerDataObserver;
    private ChatFragmentSendMessageHelper mSendHelper;
    private int mNormalToolbarInset;
    private OneTimeMessageJump mMessageJump;
    private String mAutoOpenChannel;

    public static ChatFragment newInstance(ServerConnectionInfo server, String channel, String messageId) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SERVER_UUID, server.getUUID().toString());
        if (channel != null)
            args.putString(ARG_CHANNEL_NAME, channel);
        if (messageId != null)
            args.putString(ARG_MESSAGE_ID, messageId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.chat_fragment, container, false);

        UUID connectionUUID = UUID.fromString(getArguments().getString(ARG_SERVER_UUID));
        mConnectionInfo = ServerConnectionManager.getInstance(getContext()).getConnection(connectionUUID);
        String requestedChannel = getArguments().getString(ARG_CHANNEL_NAME);
        String requestedMessageId = getArguments().getString(ARG_MESSAGE_ID);

        if (mConnectionInfo == null) {
            ((MainActivity) getActivity()).openManageServers();
            return null;
        }

        mAppBar = rootView.findViewById(R.id.appbar);

        Toolbar toolbar = rootView.findViewById(R.id.toolbar);
        mNormalToolbarInset = toolbar.getContentInsetStartWithNavigation();

        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(mConnectionInfo.getName());

        ((MainActivity) getActivity()).addActionBarDrawerToggle(toolbar);

        mSectionsPagerAdapter = new ChatPagerAdapter(this, mConnectionInfo, savedInstanceState);

        mViewPager = rootView.findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        if (requestedChannel != null)
            setCurrentChannel(requestedChannel, requestedMessageId);

        mPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int i) {
                super.onPageSelected(i);
                String channel = mSectionsPagerAdapter.getChannel(i);
                ((MainActivity) getActivity()).getDrawerHelper().setSelectedChannel(mConnectionInfo,
                        channel);
                if (channel != null)
                    DirectShareManager.publishConversation(requireContext(), mConnectionInfo,
                            channel);
            }
        };
        mViewPager.registerOnPageChangeCallback(mPageChangeCallback);

        mConnectionInfo.addOnChannelListChangeListener(this);
        mConnectionInfo.addOnChannelInfoChangeListener(this);

        mTabLayout = rootView.findViewById(R.id.tabs);
        mTabLayoutMediator = new TabLayoutMediator(mTabLayout, mViewPager,
                this::configureTab);
        mTabLayoutMediator.attach();

        mPagerDataObserver = new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                updateTabLayoutTabs();
            }
        };
        mSectionsPagerAdapter.registerAdapterDataObserver(mPagerDataObserver);
        mConnectionInfo.getNotificationManager().addUnreadMessageCountCallback(this);
        updateTabLayoutTabs();

        rootView.addOnLayoutChangeListener((View v, int left, int top, int right, int bottom,
                                            int oldLeft, int oldTop, int oldRight, int oldBottom) -> {
            int height = bottom - top;
            mAppBar.post(() -> {
                if (!isAdded())
                    return;
                if (height < getResources().getDimensionPixelSize(R.dimen.collapse_toolbar_activate_height)) {
                    mAppBar.setVisibility(View.GONE);
                } else {
                    updateToolbarCompactLayoutStatus(height);
                    mAppBar.setVisibility(View.VISIBLE);
                }
            });
        });
        mTabLayout.addOnLayoutChangeListener((View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) -> {
            if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom)
                return;
            mTabLayout.setScrollPosition(mTabLayout.getSelectedTabPosition(), 0.f, false);
        });

        mSendHelper = new ChatFragmentSendMessageHelper(this, rootView);
        String sendText = getArguments().getString(ARG_SEND_MESSAGE_TEXT);
        if (sendText != null)
            mSendHelper.setMessageText(sendText);

        SettingsHelper.registerCallbacks(this);
        onSettingChange();

        return rootView;
    }

    @Override
    public void onDestroyView() {
        if (mSectionsPagerAdapter != null && mPagerDataObserver != null)
            mSectionsPagerAdapter.unregisterAdapterDataObserver(mPagerDataObserver);
        if (mViewPager != null && mPageChangeCallback != null)
            mViewPager.unregisterOnPageChangeCallback(mPageChangeCallback);
        if (mTabLayoutMediator != null)
            mTabLayoutMediator.detach();
        mSendHelper.setCurrentChannel(null);
        if (mConnectionInfo == null) {
            super.onDestroyView();
            return;
        }
        mConnectionInfo.removeOnChannelListChangeListener(this);
        mConnectionInfo.removeOnChannelInfoChangeListener(this);
        mConnectionInfo.getNotificationManager().removeUnreadMessageCountCallback(this);
        SettingsHelper.unregisterCallbacks(this);
        super.onDestroyView();
    }

    private void updateTabLayoutTabs() {
        int count = Math.min(mSectionsPagerAdapter.getItemCount(), mTabLayout.getTabCount());
        for (int i = 0; i < count; i++)
            configureTab(mTabLayout.getTabAt(i), i);
    }

    private void configureTab(TabLayout.Tab tab, int position) {
        if (tab == null)
            return;
        tab.setText(mSectionsPagerAdapter.getPageTitle(position));
        tab.setTag(mSectionsPagerAdapter.getChannel(position));
        tab.setCustomView(R.layout.chat_tab);
        View customView = tab.getCustomView();
        if (customView == null)
            return;
        TextView textView = customView.findViewById(android.R.id.text1);
        textView.setText(mSectionsPagerAdapter.getPageTitle(position));
        textView.setTextColor(mTabLayout.getTabTextColors());
        ImageViewCompat.setImageTintList(customView.findViewById(R.id.notification_icon),
                mTabLayout.getTabTextColors());
        installTabTitleLongPress(customView, tab);
        updateTabLayoutTab(tab);
    }

    /**
     * Detects a long press without making the tab view clickable itself, so an ordinary tap
     * continues to be handled by TabLayout and changes the active conversation as before.
     */
    private void installTabTitleLongPress(View tabView, TabLayout.Tab tab) {
        final boolean[] longPressTriggered = { false };
        Runnable showMenu = () -> {
            longPressTriggered[0] = true;
            tab.select();
            FragmentActivity activity = getActivity();
            if (activity instanceof MainActivity) {
                activity.invalidateOptionsMenu();
                mTabLayout.post(() -> ((MainActivity) activity).getToolbar().showOverflowMenu());
            }
        };
        tabView.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                longPressTriggered[0] = false;
                view.postDelayed(showMenu, ViewConfiguration.getLongPressTimeout());
                return true;
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                view.removeCallbacks(showMenu);
                if (!longPressTriggered[0])
                    tab.select();
                return true;
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                view.removeCallbacks(showMenu);
                return true;
            }
            return true;
        });
    }

    private void updateTabLayoutTab(TabLayout.Tab tab) {
        String channel = (String) tab.getTag();
        boolean highlight = false;
        if (channel != null) {
            ChannelNotificationManager data = mConnectionInfo.getNotificationManager().getChannelManager(channel, false);
            if (data != null)
                highlight = data.hasUnreadMessages() || data.getMentionCount() > 0;
        }
        tab.getCustomView().findViewById(R.id.notification_icon).setVisibility(highlight ? View.VISIBLE : View.GONE);
    }

    @UiSettingChangeCallback(keys = {
            ChatSettings.PREF_APPBAR_COMPACT_MODE,
            ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED,
            ChatSettings.PREF_FONT,
            ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE,
            NickAutocompleteSettings.PREF_SHOW_BUTTON,
            NickAutocompleteSettings.PREF_DOUBLE_TAP
    })
    private void onSettingChange() {
        if (getView() != null)
            updateToolbarCompactLayoutStatus(getView().getBottom() - getView().getTop());
        mSendHelper.setTabButtonVisible(NickAutocompleteSettings.isButtonVisible());
        mSendHelper.setMessageFieldTypeface(ChatSettings.getFont());
        mSendHelper.setAutocorrectEnabled(ChatSettings.isTextAutocorrectEnabled());
        mSendHelper.setAlwaysMultiline(ChatSettings.isSendBoxAlwaysMultiline());
    }

    public void updateToolbarCompactLayoutStatus(int height) {
        String mode = ChatSettings.getAppbarCompactMode();
        boolean enabled = mode.equals(SettingsHelper.COMPACT_MODE_ALWAYS) ||
                (mode.equals(SettingsHelper.COMPACT_MODE_AUTO) &&
                        height < getResources().getDimensionPixelSize(R.dimen.compact_toolbar_activate_height));
        setUseToolbarCompactLayout(enabled);
    }

    public void setUseToolbarCompactLayout(boolean enable) {
        Toolbar toolbar = ((MainActivity) getActivity()).getToolbar();
        if (enable == (mTabLayout.getParent() == toolbar))
            return;
        ((ViewGroup) mTabLayout.getParent()).removeView(mTabLayout);
        if (enable) {
            ViewGroup.LayoutParams params = new Toolbar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mTabLayout.setLayoutParams(params);
            toolbar.addView(mTabLayout);
            toolbar.setContentInsetStartWithNavigation(0);
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mTabLayout.setLayoutParams(params);
        } else {
            mAppBar.addView(mTabLayout);
            toolbar.setContentInsetStartWithNavigation(mNormalToolbarInset);
            ViewGroup.LayoutParams params = mTabLayout.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mTabLayout.setLayoutParams(params);
        }
    }

    public void setTabsHidden(boolean hidden) {
        mTabLayout.setVisibility(hidden ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSectionsPagerAdapter != null)
            mSectionsPagerAdapter.onSaveInstanceState(outState);
    }

    public ServerConnectionInfo getConnectionInfo() {
        return mConnectionInfo;
    }

    public void setCurrentChannel(String channel, String messageId) {
        if (messageId != null)
            mMessageJump = new OneTimeMessageJump(channel, messageId);
        int i = mSectionsPagerAdapter.findChannel(channel);
        mViewPager.setCurrentItem(i);
        if (i == 0) {
            // If channel was not found, cancel the notification as we most probably came here from
            // a notification.
            ChannelNotificationManager chanMgr = mConnectionInfo.getNotificationManager()
                    .getChannelManager(channel, false);
            if (chanMgr != null)
                chanMgr.cancelNotification(getActivity());
        }
    }

    public String getAndClearMessageJump(String channel) {
        if (channel != null && mMessageJump != null && channel.equals(mMessageJump.mChannel)) {
            OneTimeMessageJump ret = mMessageJump;
            mMessageJump = null;
            return ret.mMessageId;
        }
        return null;
    }

    public void setCurrentChannelInfo(String topic, String topicSetBy, Date topicSetOn,
                                      List<NickWithPrefix> members) {
        ((MainActivity) getActivity()).setCurrentChannelInfo(getConnectionInfo(), getCurrentChannel(),
                topic, topicSetBy, topicSetOn, members);
        if (mSendHelper != null)
            mSendHelper.setCurrentChannelMembers(members);
    }

    public String getCurrentChannel() {
        return mSectionsPagerAdapter.getChannel(mViewPager.getCurrentItem());
    }

    /** Selects the tab immediately to the left after the current private query is closed. */
    public void selectTabToLeftAfterClose() {
        final int targetPosition = Math.max(0, mViewPager.getCurrentItem() - 1);
        mViewPager.post(() -> {
            if (!isAdded() || mSectionsPagerAdapter == null)
                return;
            mSectionsPagerAdapter.updateChannelList();
            mViewPager.setCurrentItem(Math.min(targetPosition,
                    mSectionsPagerAdapter.getItemCount() - 1), false);
        });
    }

    private ChatMessagesFragment getCurrentMessagesFragment() {
        String channel = getCurrentChannel();
        if (channel == null)
            return null;
        for (Fragment fragment : getChildFragmentManager().getFragments()) {
            if (fragment instanceof ChatMessagesFragment &&
                    channel.equals(((ChatMessagesFragment) fragment).getChannelName()))
                return (ChatMessagesFragment) fragment;
        }
        return null;
    }

    public void startMentionNavigation() {
        ChatMessagesFragment fragment = getCurrentMessagesFragment();
        if (fragment != null)
            fragment.startMentionNavigation();
    }

    public String getCurrentVisibleMessageId() {
        ChatMessagesFragment fragment = getCurrentMessagesFragment();
        return fragment == null ? null : fragment.getFirstVisibleMessageId();
    }

    public void markCurrentConversationRead() {
        ChatMessagesFragment fragment = getCurrentMessagesFragment();
        if (fragment != null) {
            fragment.markAsRead();
            return;
        }
        String channel = getCurrentChannel();
        if (channel != null)
            mConnectionInfo.getNotificationManager().getChannelManager(channel, true)
                    .clearUnreadMessages();
    }

    public void markCurrentMentionsRead() {
        ChatMessagesFragment fragment = getCurrentMessagesFragment();
        if (fragment != null) {
            fragment.markAllMentionsReviewed();
            return;
        }
        String channel = getCurrentChannel();
        if (channel != null)
            mConnectionInfo.getNotificationManager().getChannelManager(channel, true)
                    .markAllMentionsReviewed();
    }

    public ChatFragmentSendMessageHelper getSendMessageHelper() {
        return mSendHelper;
    }

    public void setAutoOpenChannel(String channelName) {
        mAutoOpenChannel = channelName;
        checkForAutoOpenChannel();
    }

    private void checkForAutoOpenChannel() {
        int i = mSectionsPagerAdapter.findChannel(mAutoOpenChannel);
        if (i != 0) {
            mViewPager.setCurrentItem(i);
            mAutoOpenChannel = null;
        }
    }

    @Override
    public void onConnectionInfoChanged(ServerConnectionInfo connection) {
        runOnUiThreadIfViewAttached(() -> {
            mSendHelper.updateVisibility();
        });
    }

    @Override
    public void onChannelListChanged(ServerConnectionInfo connection, List<String> newChannels) {
        runOnUiThreadIfViewAttached(() -> {
            mSectionsPagerAdapter.updateChannelList();
            checkForAutoOpenChannel();
        });
    }

    @Override
    public void onUnreadMessageCountChanged(ServerConnectionInfo info, String channel,
                                            int messageCount, int oldMessageCount) {
        runOnUiThreadIfViewAttached(() -> {
            int tabNumber = mSectionsPagerAdapter.findChannel(channel);
            TabLayout.Tab tab = mTabLayout.getTabAt(tabNumber);
            if (tab != null)
                updateTabLayoutTab(tab);
            FragmentActivity activity = getActivity();
            if (activity != null)
                activity.invalidateOptionsMenu();
        });
    }

    /**
     * Connection and notification callbacks can race with {@link #onDestroyView()}.
     * Only touch the fragment UI while it is still attached to the same activity.
     */
    private void runOnUiThreadIfViewAttached(Runnable action) {
        FragmentActivity activity = getActivity();
        if (activity == null)
            return;
        activity.runOnUiThread(() -> {
            if (!isAdded() || getActivity() != activity || getView() == null)
                return;
            action.run();
        });
    }


    private static class OneTimeMessageJump {

        private String mChannel;
        private String mMessageId;

        private OneTimeMessageJump(String channel, String messageId) {
            this.mChannel = channel;
            this.mMessageId = messageId;
        }

    }

}
