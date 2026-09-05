package io.mrarm.irc;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.GravityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AlertDialog;

import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.mrarm.chatlib.ChatApi;
import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.chatlib.irc.ServerConnectionApi;
import io.mrarm.chatlib.irc.dcc.DCCServer;
import io.mrarm.chatlib.irc.dcc.DCCUtils;
import io.mrarm.irc.chat.ChannelInfoAdapter;
import io.mrarm.irc.chat.ChatFragment;
import io.mrarm.irc.config.AppSettings;
import io.mrarm.irc.config.SharingSettings;
import io.mrarm.irc.dialog.UserSearchDialog;
import io.mrarm.irc.dialog.ChatMessageSearchDialog;
import io.mrarm.irc.dialog.ChannelBanListDialog;
import io.mrarm.irc.dialog.ChannelModesDialog;
import io.mrarm.irc.dialog.SimosnapSendMenu;
import io.mrarm.irc.dialog.UserBottomSheetDialog;
import io.mrarm.irc.dialog.VoiceRecorderDialog;
import io.mrarm.irc.drawer.DrawerHelper;
import io.mrarm.irc.upload.SimosnapUploader;
import io.mrarm.irc.util.ChannelOperatorUtils;
import io.mrarm.irc.util.NightModeRecreateHelper;
import io.mrarm.irc.util.StyledAttributesHelper;
import io.mrarm.irc.util.WarningHelper;
import io.mrarm.irc.view.ChipsEditText;
import io.mrarm.irc.view.LockableDrawerLayout;

public class MainActivity extends ThemedActivity implements IRCApplication.ExitCallback {

    public static final String ARG_SERVER_UUID = "server_uuid";
    public static final String ARG_CHANNEL_NAME = "channel";
    public static final String ARG_MESSAGE_ID = "message_id";
    public static final String ARG_MANAGE_SERVERS = "manage_servers";

    private static final int REQUEST_CODE_RECORD_AUDIO_PERMISSION = 106;
    private static final int REQUEST_CODE_RECORD_VIDEO_PERMISSION = 107;
    private static final int REQUEST_CODE_CAPTURE_PHOTO_PERMISSION = 108;

    private NightModeRecreateHelper mNightModeHelper = new NightModeRecreateHelper(this);
    private LockableDrawerLayout mDrawerLayout;
    private DrawerHelper mDrawerHelper;
    private Toolbar mToolbar;
    private View mFakeToolbar;
    private boolean mBackReturnToServerList;
    private Dialog mCurrentDialog;
    private ChannelInfoAdapter mChannelInfoAdapter;
    private boolean mAppExiting;
    private ServerConnectionInfo mSimosnapUploadConnection;
    private String mSimosnapUploadTarget;
    private ServerConnectionInfo mDccUploadConnection;
    private String mDccUploadTarget;
    private Uri mSimosnapCaptureUri;
    private ActivityResultLauncher<Intent> mPickDccFileLauncher;
    private ActivityResultLauncher<Intent> mPickSimosnapFileLauncher;
    private ActivityResultLauncher<Intent> mCaptureSimosnapPhotoLauncher;
    private ActivityResultLauncher<Intent> mRecordSimosnapVideoLauncher;
    private ActivityResultLauncher<Intent> mDccDownloadsDirectoryLauncher;
    private DCCManager.ActivityDialogHandler mDCCDialogHandler =
            new DCCManager.ActivityDialogHandler(this);

    public static Intent getLaunchIntent(Context context, ServerConnectionInfo server, String channel, String messageId) {
        Intent intent = new Intent(context, MainActivity.class);
        if (server != null)
            intent.putExtra(ARG_SERVER_UUID, server.getUUID().toString());
        if (channel != null)
            intent.putExtra(ARG_CHANNEL_NAME, channel);
        if (messageId != null)
            intent.putExtra(ARG_MESSAGE_ID, messageId);
        return intent;
    }

    public static Intent getLaunchIntent(Context context, ServerConnectionInfo server, String channel) {
        return getLaunchIntent(context, server, channel, null);
    }

    public static Intent getLaunchIntentForManageServers(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(ARG_MANAGE_SERVERS, true);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ServerConnectionManager.getInstance(this);
        WarningHelper.setAppContext(getApplicationContext());

        mAppExiting = false;
        ((IRCApplication) getApplication()).addExitCallback(this);

        super.onCreate(savedInstanceState);
        registerActivityResultLaunchers();
        setContentView(R.layout.activity_main);

        mFakeToolbar = findViewById(R.id.fake_toolbar);

        mDrawerLayout = findViewById(R.id.drawer_layout);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (handleMainBackPressed())
                    return;
                setEnabled(false);
                try {
                    getOnBackPressedDispatcher().onBackPressed();
                } finally {
                    setEnabled(true);
                }
            }
        });

        mDrawerHelper = new DrawerHelper(this);
        mDrawerHelper.registerListeners();

        mDrawerHelper.setChannelClickListener((ServerConnectionInfo server, String channel) -> {
            mDrawerLayout.closeDrawers();
            Fragment f = getCurrentFragment();
            if (f != null && f instanceof ChatFragment && ((ChatFragment) f).getConnectionInfo() == server)
                ((ChatFragment) f).setCurrentChannel(channel, null);
            else
                openServer(server, channel);
        });
        mDrawerHelper.setChannelLongClickListener((ServerConnectionInfo server, String channel) -> {
            mDrawerLayout.closeDrawers();
            ChatFragment fragment;
            Fragment f = getCurrentFragment();
            if (f instanceof ChatFragment && ((ChatFragment) f).getConnectionInfo() == server) {
                fragment = (ChatFragment) f;
            } else {
                fragment = openServer(server, channel);
                getSupportFragmentManager().executePendingTransactions();
            }
            fragment.showChannelActions(channel);
        });
        mDrawerHelper.getManageServersItem().setOnClickListener((View v) -> {
            mDrawerLayout.closeDrawers();
            openManageServers();
        });

        if (AppSettings.isDrawerPinned())
            mDrawerLayout.setLocked(true);

        mChannelInfoAdapter = new ChannelInfoAdapter();
        RecyclerView membersRecyclerView = findViewById(R.id.members_list);
        membersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        membersRecyclerView.setAdapter(mChannelInfoAdapter);
        mDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerClosed(View drawerView) {
                if (drawerView.getId() == R.id.members_nav_view)
                    mChannelInfoAdapter.onDrawerClosed();
            }
        });
        setChannelInfoDrawerVisible(false);

        if (savedInstanceState != null && savedInstanceState.getString(ARG_SERVER_UUID) != null)
            return;

        handleIntent(getIntent());
    }

    private void registerActivityResultLaunchers() {
        ActivityResultContracts.StartActivityForResult contract =
                new ActivityResultContracts.StartActivityForResult();
        mPickDccFileLauncher = registerForActivityResult(contract, this::handleDccFileResult);
        mPickSimosnapFileLauncher = registerForActivityResult(contract,
                this::handleSimosnapFileResult);
        mCaptureSimosnapPhotoLauncher = registerForActivityResult(contract,
                result -> handleSimosnapCaptureResult(result.getResultCode()));
        mRecordSimosnapVideoLauncher = registerForActivityResult(contract,
                this::handleSimosnapVideoResult);
        mDccDownloadsDirectoryLauncher = registerForActivityResult(contract, result ->
                mDCCDialogHandler.handleDownloadsDirectoryResult(
                        result.getResultCode(), result.getData()));
        mDCCDialogHandler.setDownloadsDirectoryLauncher(mDccDownloadsDirectoryLauncher);
    }

    private void handleIntent(Intent intent) {
        DirectShareManager.applyTarget(intent);
        String serverUUID = intent.getStringExtra(ARG_SERVER_UUID);
        ServerConnectionInfo server = null;
        if (serverUUID != null)
            server = ServerConnectionManager.getInstance(this).getConnection(
                    UUID.fromString(serverUUID));
        if (server != null) {
            String channel = intent.getStringExtra(ARG_CHANNEL_NAME);
            if (channel != null && server.getApiInstance() instanceof ServerConnectionApi &&
                    !channel.isEmpty() && !((ServerConnectionApi) server.getApiInstance())
                    .getServerConnectionData().getSupportList().getSupportedChannelTypes()
                    .contains(channel.charAt(0)))
                server.addStoredConversation(channel);
            ChatFragment fragment = openServer(server, channel,
                    intent.getStringExtra(ARG_MESSAGE_ID), false);
            if (Intent.ACTION_SEND.equals(intent.getAction()) &&
                    "text/plain".equals(intent.getType())) {
                setFragmentShareText(fragment, intent.getStringExtra(Intent.EXTRA_TEXT));
            }
        } else if (intent.getBooleanExtra(ARG_MANAGE_SERVERS, false) ||
                getCurrentFragment() == null) {
            openManageServers();
        } else {
            mBackReturnToServerList = false;
        }
    }

    private void setFragmentShareText(ChatFragment fragment, String text) {
        if (fragment.getSendMessageHelper() != null) {
            fragment.getSendMessageHelper().setMessageText(text);
        } else {
            Bundle bundle = fragment.getArguments();
            bundle.putString(ChatFragment.ARG_SEND_MESSAGE_TEXT, text);
            fragment.setArguments(bundle);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        StyledAttributesHelper ta = StyledAttributesHelper.obtainStyledAttributes(this,
                new int[] { R.attr.actionBarSize });
        ViewGroup.LayoutParams params = mFakeToolbar.getLayoutParams();
        params.height = ta.getDimensionPixelSize(R.attr.actionBarSize, 0);
        mFakeToolbar.setLayoutParams(params);
        ta.recycle();
        if (mToolbar != null) {
            ViewGroup group = (ViewGroup) mToolbar.getParent();
            int i = group.indexOfChild(mToolbar);
            group.removeViewAt(i);
            Toolbar replacement = new Toolbar(group.getContext());
            replacement.setPopupTheme(mToolbar.getPopupTheme());
            AppBarLayout.LayoutParams toolbarParams = new AppBarLayout.LayoutParams(
                    AppBarLayout.LayoutParams.MATCH_PARENT, params.height);
            replacement.setLayoutParams(toolbarParams);
            for (int j = 0; j < mToolbar.getChildCount(); j++) {
                View v = mToolbar.getChildAt(j);
                if (v instanceof TabLayout) {
                    mToolbar.removeViewAt(j);
                    replacement.addView(v);
                    j--;
                }
            }
            group.addView(replacement, i);
            setSupportActionBar(replacement);
            addActionBarDrawerToggle(replacement);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (getCurrentFragment() instanceof ChatFragment) {
            ChatFragment chat = ((ChatFragment) getCurrentFragment());
            outState.putString(ARG_SERVER_UUID, chat.getConnectionInfo().getUUID().toString());
            outState.putString(ARG_CHANNEL_NAME, chat.getCurrentChannel());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        String serverUUID = savedInstanceState.getString(ARG_SERVER_UUID);
        if (serverUUID != null) {
            ServerConnectionInfo server = ServerConnectionManager.getInstance(this).getConnection(UUID.fromString(serverUUID));
            openServer(server, savedInstanceState.getString(ARG_CHANNEL_NAME));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mNightModeHelper.onStart();
    }

    @Override
    protected void onDestroy() {
        ((IRCApplication) getApplication()).removeExitCallback(this);
        mDrawerHelper.unregisterListeners();
        dismissFragmentDialog();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WarningHelper.setActivity(this);
        mDCCDialogHandler.onResume();
        if (getCurrentFragment() instanceof ChatFragment && !ServerConnectionManager
                .getInstance(this).hasConnection(((ChatFragment) getCurrentFragment())
                        .getConnectionInfo().getUUID())) {
            openManageServers();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        WarningHelper.setActivity(null);
        mDCCDialogHandler.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        mToolbar = toolbar;
    }

    public Toolbar getToolbar() {
        return mToolbar;
    }

    public void addActionBarDrawerToggle(Toolbar toolbar) {
        LockableDrawerLayout.ActionBarDrawerToggle toggle = new LockableDrawerLayout.ActionBarDrawerToggle(
                toolbar, mDrawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        // mDrawerLayout.addDrawerListener(toggle);
    }

    public ChatFragment openServer(ServerConnectionInfo server, String channel, String messageId,
                                   boolean fromServerList) {
        dismissFragmentDialog();
        setChannelInfoDrawerVisible(false);
        ChatFragment fragment;
        if (getCurrentFragment() instanceof ChatFragment &&
                ((ChatFragment) getCurrentFragment()).getConnectionInfo() == server) {
            fragment = (ChatFragment) getCurrentFragment();
            fragment.setCurrentChannel(channel, messageId);
            setChannelInfoDrawerVisible(false);
        } else {
            fragment = ChatFragment.newInstance(server, channel, messageId);
            getSupportFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.content_frame, fragment)
                    .commit();
        }
        mDrawerHelper.setSelectedChannel(server, channel);
        if (fromServerList)
            mBackReturnToServerList = true;
        return fragment;
    }

    public ChatFragment openServer(ServerConnectionInfo server, String channel) {
        return openServer(server, channel, null, false);
    }

    public void openManageServers() {
        dismissFragmentDialog();
        setChannelInfoDrawerVisible(false);
        getSupportFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.content_frame, ServerListFragment.newInstance())
                .commit();
        mDrawerHelper.setSelectedMenuItem(mDrawerHelper.getManageServersItem());
        mBackReturnToServerList = false;
    }

    public DrawerHelper getDrawerHelper() {
        return mDrawerHelper;
    }

    public Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.content_frame);
    }

    public void setFragmentDialog(Dialog dialog) {
        if (mCurrentDialog != null) {
            mCurrentDialog.setOnDismissListener(null);
            mCurrentDialog.dismiss();
        }
        mCurrentDialog = dialog;
        mCurrentDialog.setOnDismissListener((DialogInterface di) -> {
            if (mCurrentDialog == dialog)
                mCurrentDialog = null;
        });
    }

    public void dismissFragmentDialog() {
        if (mCurrentDialog != null) {
            InputMethodManager manager = (InputMethodManager) getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(mCurrentDialog.getWindow().getDecorView()
                    .getApplicationWindowToken(), 0);

            mCurrentDialog.setOnDismissListener(null);
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
        }
    }

    public void setCurrentChannelInfo(ServerConnectionInfo server, String channel, String topic, String topicSetBy,
                                      Date topicSetOn, List<NickWithPrefix> members) {
        if (mChannelInfoAdapter == null)
            return;
        mChannelInfoAdapter.setData(server, channel, topic, topicSetBy, topicSetOn, members);
        setChannelInfoDrawerVisible(topic != null || (members != null && members.size() > 0));
    }

    public void setChannelInfoDrawerVisible(boolean visible) {
        if (visible) {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END);
        } else {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END);
            mDrawerLayout.closeDrawer(GravityCompat.END);
        }
    }

    private boolean handleMainBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.END)) {
            mDrawerLayout.closeDrawer(GravityCompat.END);
            return true;
        }
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START) && !mDrawerLayout.isCurrentlyLocked()) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        if (!(getCurrentFragment() instanceof ServerListFragment)) {
            openManageServers();
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (getCurrentFragment() instanceof ChatFragment) {
            getMenuInflater().inflate(R.menu.menu_chat, menu);
        } else if (getCurrentFragment() instanceof ServerListFragment) {
            getMenuInflater().inflate(R.menu.menu_server_list, menu);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean hasChanges = false;
        if (getCurrentFragment() instanceof ChatFragment) {
            ChatFragment fragment = ((ChatFragment) getCurrentFragment());
            ServerConnectionApi api = ((ServerConnectionApi) fragment.getConnectionInfo().getApiInstance());
            boolean connected = fragment.getConnectionInfo().isConnected();
            boolean wasConnected = !menu.findItem(R.id.action_reconnect).isVisible();
            if (connected != wasConnected) {
                if (connected) {
                    menu.findItem(R.id.action_reconnect).setVisible(false);
                    menu.findItem(R.id.action_close).setVisible(false);
                    menu.findItem(R.id.action_disconnect).setVisible(true);
                    menu.findItem(R.id.action_disconnect_and_close).setVisible(true);
                } else {
                    menu.findItem(R.id.action_reconnect).setVisible(true);
                    menu.findItem(R.id.action_close).setVisible(true);
                    menu.findItem(R.id.action_disconnect).setVisible(false);
                    menu.findItem(R.id.action_disconnect_and_close).setVisible(false);
                }
                hasChanges = true;
            }
            menu.findItem(R.id.action_members).setVisible(
                    mDrawerLayout.getDrawerLockMode(GravityCompat.END) != DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            if (fragment.getSendMessageHelper().hasSendMessageTextSelection() !=
                    menu.findItem(R.id.action_format).isVisible()) {
                menu.findItem(R.id.action_format).setVisible(fragment.getSendMessageHelper()
                        .hasSendMessageTextSelection());
                hasChanges = true;
            }
            MenuItem partItem = menu.findItem(R.id.action_part_channel);
            boolean inDirectChat = false;
            if (fragment.getCurrentChannel() == null) {
                if (partItem.isVisible())
                    hasChanges = true;
                partItem.setVisible(false);
            } else if (fragment.getCurrentChannel().length() > 0 && !api.getServerConnectionData()
                    .getSupportList().getSupportedChannelTypes().contains(fragment.getCurrentChannel().charAt(0))) {
                if (partItem.isVisible() || !partItem.getTitle().equals(getString(R.string.action_close_direct)))
                    hasChanges = true;
                partItem.setVisible(true);
                partItem.setTitle(R.string.action_close_direct);
                inDirectChat = true;
            } else {
                if (partItem.isVisible() || !partItem.getTitle().equals(getString(R.string.action_part_channel)))
                    hasChanges = true;
                partItem.setVisible(true);
                partItem.setTitle(R.string.action_part_channel);
            }
            menu.findItem(R.id.action_send_media).setVisible(connected && inDirectChat &&
                    SharingSettings.hasAnySendOption(this));
            menu.findItem(R.id.action_direct_whois).setVisible(connected && inDirectChat);
            String current = fragment.getCurrentChannel();
            menu.findItem(R.id.action_list_channels).setVisible(connected && current == null);
            ChannelNotificationManager notificationManager = current == null ? null :
                    fragment.getConnectionInfo().getNotificationManager()
                            .getChannelManager(current, true);
            menu.findItem(R.id.action_mentions).setVisible(current != null);
            menu.findItem(R.id.action_mark_read).setVisible(notificationManager != null &&
                    notificationManager.getUnreadMessageCount() > 0);
            menu.findItem(R.id.action_mark_mentions_read).setVisible(notificationManager != null &&
                    notificationManager.getMentionCount() > 0);
            boolean operatorChannel = connected && current != null && !current.isEmpty() &&
                    api.getServerConnectionData().getSupportList().getSupportedChannelTypes()
                            .contains(current.charAt(0)) &&
                    ChannelOperatorUtils.hasOperatorPrivileges(fragment.getConnectionInfo(),
                            current, false);
            menu.findItem(R.id.action_channel_modes).setVisible(operatorChannel);
            menu.findItem(R.id.action_channel_bans).setVisible(operatorChannel);
        }
        return super.onPrepareOptionsMenu(menu) | hasChanges;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_list_channels) {
            openChannelList();
        } else if (id == R.id.action_join_channel) {
            View v = LayoutInflater.from(this).inflate(R.layout.dialog_chip_edit_text, null);
            ChipsEditText editText = v.findViewById(R.id.chip_edit_text);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.action_join_channel)
                    .setView(v)
                    .setPositiveButton(R.string.action_ok, (DialogInterface d, int which) -> {
                        editText.clearFocus();
                        String[] channels = editText.getItems();
                        if (channels.length == 0)
                            return;
                        ChatFragment currentChat = (ChatFragment) getCurrentFragment();
                        ChatApi api = currentChat.getConnectionInfo().getApiInstance();
                        currentChat.setAutoOpenChannel(channels[0]);
                        api.joinChannels(Arrays.asList(channels), null, null);
                    })
                    .setNeutralButton(R.string.title_activity_channel_list, (DialogInterface d, int which) -> {
                        openChannelList();
                    })
                    .create();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            dialog.show();
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            setFragmentDialog(dialog);
        } else if (id == R.id.action_message_user) {
            UserSearchDialog dialog = new UserSearchDialog(this, ((ChatFragment)
                    getCurrentFragment()).getConnectionInfo());
            dialog.show();
            setFragmentDialog(dialog);
        } else if (id == R.id.action_search_messages) {
            ChatFragment fragment = (ChatFragment) getCurrentFragment();
            if (fragment.getCurrentChannel() != null)
                new ChatMessageSearchDialog(this, fragment).show();
        } else if (id == R.id.action_mentions) {
            ((ChatFragment) getCurrentFragment()).startMentionNavigation();
        } else if (id == R.id.action_mark_read) {
            ((ChatFragment) getCurrentFragment()).markCurrentConversationRead();
        } else if (id == R.id.action_mark_mentions_read) {
            ((ChatFragment) getCurrentFragment()).markCurrentMentionsRead();
        } else if (id == R.id.action_channel_modes) {
            ChatFragment fragment = (ChatFragment) getCurrentFragment();
            new ChannelModesDialog(this, fragment.getConnectionInfo(),
                    fragment.getCurrentChannel()).show();
        } else if (id == R.id.action_channel_bans) {
            ChatFragment fragment = (ChatFragment) getCurrentFragment();
            new ChannelBanListDialog(this, fragment.getConnectionInfo(),
                    fragment.getCurrentChannel()).show();
        } else if (id == R.id.action_send_media) {
            ChatFragment fragment = (ChatFragment) getCurrentFragment();
            SimosnapSendMenu.show(this, fragment.getConnectionInfo(),
                    fragment.getCurrentChannel());
        } else if (id == R.id.action_direct_whois) {
            ChatFragment fragment = (ChatFragment) getCurrentFragment();
            String nick = fragment.getCurrentChannel();
            if (nick != null && !nick.isEmpty()) {
                UserBottomSheetDialog dialog = new UserBottomSheetDialog(this);
                dialog.setConnection(fragment.getConnectionInfo());
                dialog.requestData(nick, fragment.getConnectionInfo().getApiInstance());
                setFragmentDialog(dialog.show());
            }
        } else if (id == R.id.action_part_channel) {
            ChatFragment fragment = (ChatFragment) getCurrentFragment();
            ServerConnectionInfo connection = fragment.getConnectionInfo();
            ChatApi api = connection.getApiInstance();
            String channel = fragment.getCurrentChannel();
            if (channel != null) {
                boolean directConversation = channel.length() > 0 &&
                        !((ServerConnectionApi) api).getServerConnectionData().getSupportList()
                                .getSupportedChannelTypes().contains(channel.charAt(0));
                if (directConversation) {
                    connection.closePrivateConversation(channel,
                            AppSettings.getDefaultPartMessage());
                    fragment.selectTabToLeftAfterClose();
                } else {
                    api.leaveChannel(channel, AppSettings.getDefaultPartMessage(), null, null);
                }
            }
        } else if (id == R.id.action_members) {
            mDrawerLayout.openDrawer(GravityCompat.END);
        } else if (id == R.id.action_ignore_list) {
            ServerConnectionInfo info = ((ChatFragment) getCurrentFragment()).getConnectionInfo();
            Intent intent = new Intent(this, IgnoreListActivity.class);
            intent.putExtra(IgnoreListActivity.ARG_SERVER_UUID, info.getUUID().toString());
            startActivity(intent);
        } else if (id == R.id.action_monitored_users) {
            Fragment current = getCurrentFragment();
            ServerConnectionInfo info = (current instanceof ChatFragment) ?
                    ((ChatFragment) current).getConnectionInfo() : null;
            MonitoredUsersActivity.open(this, info);
        } else if (id == R.id.action_disconnect) {
            ((ChatFragment) getCurrentFragment()).getConnectionInfo().disconnect();
        } else if (id == R.id.action_disconnect_and_close || id == R.id.action_close) {
            ServerConnectionInfo info = ((ChatFragment) getCurrentFragment()).getConnectionInfo();
            info.disconnect();
            ServerConnectionManager.getInstance(this).removeConnection(info);
            openManageServers();
        } else if (id == R.id.action_reconnect) {
            ((ChatFragment) getCurrentFragment()).getConnectionInfo().connect();
        } else if (id == R.id.action_format) {
            ((ChatFragment) getCurrentFragment()).getSendMessageHelper().setFormatBarVisible(true);
        } else if (id == R.id.action_dcc_transfers) {
            startActivity(new Intent(this, DCCActivity.class));
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.action_exit) {
            ((IRCApplication) getApplication()).requestExit();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void openChannelList() {
        if (!(getCurrentFragment() instanceof ChatFragment))
            return;
        ServerConnectionInfo info = ((ChatFragment) getCurrentFragment()).getConnectionInfo();
        Intent intent = new Intent(this, ChannelListActivity.class);
        intent.putExtra(ChannelListActivity.ARG_SERVER_UUID, info.getUUID().toString());
        startActivity(intent);
    }

    public void openDirectConversationForSharing(ServerConnectionInfo connection, String target) {
        if (connection == null || target == null || target.trim().isEmpty())
            return;
        String nick = target.trim();
        if (connection.getApiInstance() instanceof ServerConnectionApi) {
            ServerConnectionApi api = (ServerConnectionApi) connection.getApiInstance();
            if (!nick.isEmpty() && api.getServerConnectionData().getSupportList()
                    .getSupportedChannelTypes().contains(nick.charAt(0)))
                return;
        }
        connection.registerPrivateConversation(nick,
                () -> runOnUiThread(() -> openServer(connection, nick)));
    }

    private void handleSimosnapFileResult(ActivityResult result) {
        Intent data = result.getData();
        if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null &&
                mSimosnapUploadConnection != null && mSimosnapUploadTarget != null) {
            SimosnapUploader.confirmAndUpload(this, mSimosnapUploadConnection,
                    mSimosnapUploadTarget, data.getData());
        }
        mSimosnapUploadConnection = null;
        mSimosnapUploadTarget = null;
    }

    private void handleSimosnapCaptureResult(int resultCode) {
        if (resultCode == RESULT_OK && mSimosnapCaptureUri != null &&
                mSimosnapUploadConnection != null && mSimosnapUploadTarget != null) {
            SimosnapUploader.confirmAndUpload(this, mSimosnapUploadConnection,
                    mSimosnapUploadTarget, mSimosnapCaptureUri);
        }
        mSimosnapCaptureUri = null;
        mSimosnapUploadConnection = null;
        mSimosnapUploadTarget = null;
    }

    private void handleSimosnapVideoResult(ActivityResult result) {
        Intent data = result.getData();
        if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null &&
                mSimosnapUploadConnection != null && mSimosnapUploadTarget != null) {
            SimosnapUploader.confirmAndUpload(this, mSimosnapUploadConnection,
                    mSimosnapUploadTarget, data.getData());
        }
        mSimosnapUploadConnection = null;
        mSimosnapUploadTarget = null;
    }

    private void handleDccFileResult(ActivityResult result) {
        Intent data = result.getData();
        if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null) {
            mDccUploadConnection = null;
            mDccUploadTarget = null;
            return;
        }
        Uri uri = data.getData();
        try {
            if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some providers only offer the immediate read grant, which is still usable.
        }
        String name;
        long size;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst())
                throw new IOException("Unable to read file metadata");
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            if (nameIndex < 0)
                throw new IOException("Missing file name");
            name = DCCUtils.escapeFilename(cursor.getString(nameIndex));
            size = sizeIndex < 0 || cursor.isNull(sizeIndex) ? -1 : cursor.getLong(sizeIndex);
        } catch (IOException error) {
            mDccUploadConnection = null;
            mDccUploadTarget = null;
            Toast.makeText(this, R.string.error_file_open, Toast.LENGTH_SHORT).show();
            return;
        }

        ServerConnectionInfo connection = mDccUploadConnection;
        String target = mDccUploadTarget;
        mDccUploadConnection = null;
        mDccUploadTarget = null;
        if (connection == null || target == null)
            return;
        try {
            if (size == -1) {
                try (ParcelFileDescriptor descriptor = getContentResolver()
                        .openFileDescriptor(uri, "r")) {
                    if (descriptor == null)
                        throw new IOException();
                    size = descriptor.getStatSize();
                }
            }
            DCCServer.FileChannelFactory fileFactory = () -> {
                ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri,
                        "r");
                if (descriptor == null)
                    throw new IOException("Unable to open selected file");
                return new ParcelFileDescriptor.AutoCloseInputStream(descriptor).getChannel()
                        .position(0);
            };
            DCCManager.getInstance(this).startUpload(connection, target, fileFactory, name, size);
        } catch (IOException error) {
            Toast.makeText(this, R.string.error_file_open, Toast.LENGTH_SHORT).show();
        }
    }

    public void pickSimosnapFile(ServerConnectionInfo connection, String targetNick) {
        pickSimosnapFile(connection, targetNick, "*/*");
    }

    public void pickDccFile(ServerConnectionInfo connection, String targetNick) {
        mDccUploadConnection = connection;
        mDccUploadTarget = targetNick;
        // DCC may start after the picker is closed: request a durable document URI instead of
        // relying on the short-lived GET_CONTENT grant.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        mPickDccFileLauncher.launch(intent);
    }

    public void pickSimosnapFile(ServerConnectionInfo connection, String targetNick,
                                 String mimeType) {
        mSimosnapUploadConnection = connection;
        mSimosnapUploadTarget = targetNick;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        if ("*/*".equals(mimeType))
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                    "image/*", "video/*", "audio/*", "application/pdf", "text/plain" });
        mPickSimosnapFileLauncher.launch(intent);
    }

    public void captureSimosnapMedia(ServerConnectionInfo connection, String targetNick,
                                     boolean video) {
        if (video) {
            recordSimosnapVideo(connection, targetNick);
            return;
        }
        mSimosnapUploadConnection = connection;
        mSimosnapUploadTarget = targetNick;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.CAMERA },
                    REQUEST_CODE_CAPTURE_PHOTO_PERMISSION);
            return;
        }
        startSimosnapPhotoCapture();
    }

    private void startSimosnapPhotoCapture() {
        try {
            File directory = new File(getCacheDir(), "uploads");
            if (!directory.exists() && !directory.mkdirs())
                throw new IOException("Cannot create capture directory");
            File output = new File(directory, "photo-" + System.currentTimeMillis() + ".jpg");
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", output);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            mSimosnapCaptureUri = uri;
            mCaptureSimosnapPhotoLauncher.launch(intent);
        } catch (Exception error) {
            Toast.makeText(this, R.string.media_capture_failed, Toast.LENGTH_LONG).show();
        }
    }

    public void recordSimosnapVoice(ServerConnectionInfo connection, String targetNick) {
        mSimosnapUploadConnection = connection;
        mSimosnapUploadTarget = targetNick;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO },
                    REQUEST_CODE_RECORD_AUDIO_PERMISSION);
            return;
        }
        VoiceRecorderDialog.show(this, connection, targetNick);
    }

    public void recordSimosnapVideo(ServerConnectionInfo connection, String targetNick) {
        mSimosnapUploadConnection = connection;
        mSimosnapUploadTarget = targetNick;
        List<String> missing = new java.util.ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.CAMERA);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.RECORD_AUDIO);
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]),
                    REQUEST_CODE_RECORD_VIDEO_PERMISSION);
            return;
        }
        startSimosnapVideoRecorder();
    }

    private void startSimosnapVideoRecorder() {
        mRecordSimosnapVideoLauncher.launch(new Intent(this, VideoRecorderActivity.class));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    mSimosnapUploadConnection != null && mSimosnapUploadTarget != null)
                VoiceRecorderDialog.show(this, mSimosnapUploadConnection,
                        mSimosnapUploadTarget);
            else
                Toast.makeText(this, R.string.voice_permission_required,
                        Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == REQUEST_CODE_RECORD_VIDEO_PERMISSION) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults)
                granted &= result == PackageManager.PERMISSION_GRANTED;
            if (granted && mSimosnapUploadConnection != null &&
                    mSimosnapUploadTarget != null)
                startSimosnapVideoRecorder();
            else {
                Toast.makeText(this, R.string.video_recorder_permission_required,
                        Toast.LENGTH_LONG).show();
                mSimosnapUploadConnection = null;
                mSimosnapUploadTarget = null;
            }
            return;
        }
        if (requestCode == REQUEST_CODE_CAPTURE_PHOTO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    mSimosnapUploadConnection != null && mSimosnapUploadTarget != null) {
                startSimosnapPhotoCapture();
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show();
                mSimosnapUploadConnection = null;
                mSimosnapUploadTarget = null;
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onAppExiting() {
        mAppExiting = true;
        if (getCurrentFragment() instanceof ServerListFragment)
            ((ServerListFragment) getCurrentFragment()).getAdapter().unregisterListeners();
        getDrawerHelper().unregisterListeners();
    }

    public boolean isAppExiting() {
        return mAppExiting;
    }
}
