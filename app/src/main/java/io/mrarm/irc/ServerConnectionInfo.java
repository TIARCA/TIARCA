package io.mrarm.irc;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import io.mrarm.chatlib.ChannelListListener;
import io.mrarm.chatlib.ChatApi;
import io.mrarm.chatlib.android.storage.SQLiteMessageStorageApi;
import io.mrarm.chatlib.android.storage.SQLiteMiscStorage;
import io.mrarm.chatlib.android.storage.SQLiteChannelDataStorage;
import io.mrarm.chatlib.dto.MessageId;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.IRCConnectionRequest;
import io.mrarm.chatlib.irc.ServerConnectionApi;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.irc.cap.SASLCapability;
import io.mrarm.chatlib.irc.cap.SASLOptions;
import io.mrarm.chatlib.irc.filters.ZNCPlaybackMessageFilter;
import io.mrarm.chatlib.irc.handlers.MessageCommandHandler;
import io.mrarm.chatlib.irc.handlers.ModeCommandHandler;
import io.mrarm.chatlib.message.MessageStorageApi;
import io.mrarm.irc.chat.ChatUIData;
import io.mrarm.irc.config.AppSettings;
import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.config.ServerConfigManager;
import io.mrarm.irc.util.IgnoreListMessageFilter;
import io.mrarm.irc.util.StubMessageStorageApi;
import io.mrarm.irc.util.UserAutoRunCommandHelper;
import io.mrarm.irc.irc.ChannelModeSnapshotHandler;
import io.mrarm.irc.irc.WhoXAccountHandler;
import io.mrarm.irc.irc.MonitoredUsersManager;
import io.mrarm.irc.irc.ServiceMessageCommandHandler;
import io.mrarm.irc.irc.SelfKickCommandHandler;
import io.mrarm.chatlib.irc.handlers.KickCommandHandler;

public class ServerConnectionInfo {

    private static Handler mReconnectHandler = new Handler(Looper.getMainLooper());

    private ServerConnectionManager mManager;
    private ServerConfigData mServerConfig;
    private List<String> mChannels;
    private ChatApi mApi;
    private IRCConnectionRequest mConnectionRequest;
    private SASLOptions mSASLOptions;
    private SQLiteMiscStorage mSQLiteMiscStorage;
    private boolean mExpandedInDrawer = true;
    private boolean mConnected = false;
    private boolean mConnecting = false;
    private boolean mDisconnecting = false;
    private boolean mUserDisconnectRequest = false;
    private long mReconnectQueueTime = -1L;
    private NotificationManager.ConnectionManager mNotificationData;
    private UserAutoRunCommandHelper mAutoRunHelper;
    private final List<InfoChangeListener> mInfoListeners = new ArrayList<>();
    private final List<ChannelListChangeListener> mChannelsListeners = new ArrayList<>();
    private int mCurrentReconnectAttempt = -1;
    private int mActiveAddressIndex = 0;
    /** Number of alternative endpoints tried immediately in the current failover round. */
    private int mEndpointFailoverAttempts = 0;
    int mChatLogStorageUpdateCounter = 0;
    private final ChatUIData mChatUIData = new ChatUIData();
    private final Set<String> mServiceNicks = new LinkedHashSet<>();
    /** Old query nick -> current nick, for stale channel-list callbacks after NICK. */
    private final Map<String, String> mQueryNickAliases = new LinkedHashMap<>();
    private final MonitoredUsersManager mMonitoredUsers;
    private final MonitoredUsersNotificationManager mMonitoredUsersNotifications;

    public ServerConnectionInfo(ServerConnectionManager manager, ServerConfigData config,
                                IRCConnectionRequest connectionRequest, SASLOptions saslOptions,
                                List<String> joinChannels) {
        mManager = manager;
        mServerConfig = config;
        mConnectionRequest = connectionRequest;
        mSASLOptions = saslOptions;
        mNotificationData = new NotificationManager.ConnectionManager(this);
        mMonitoredUsers = new MonitoredUsersManager(config, () ->
                ServerConfigManager.getInstance(mManager.getContext()).saveServerConfiguration(mServerConfig));
        mMonitoredUsersNotifications = new MonitoredUsersNotificationManager(mManager.getContext(), this);
        mMonitoredUsers.addListener(mMonitoredUsersNotifications);
        mChannels = joinChannels;
        if (mChannels != null)
            Collections.sort(mChannels, String::compareToIgnoreCase);
    }

    private void setApi(ChatApi api) {
        synchronized (this) {
            mApi = api;
            api.getJoinedChannelList((List<String> channels) -> {
                setChannels(channels);
            }, null);
            api.subscribeChannelList(new ChannelListListener() {
                @Override
                public void onChannelListChanged(List<String> list) {
                    setChannels(list);
                }

                @Override
                public void onChannelJoined(String s) {
                }

                @Override
                public void onChannelLeft(String s) {
                }
            }, null, null);
            mChatUIData.attachToConnection(api);
        }
    }

    public ServerConnectionManager getConnectionManager() {
        return mManager;
    }

    public void connect() {
        synchronized (this) {
            if (mDisconnecting)
                throw new RuntimeException("Trying to connect with mDisconnecting set");
            if (mConnected || mConnecting)
                return;
            mConnecting = true;
            mUserDisconnectRequest = false;
            mReconnectQueueTime = -1L;
        }
        Log.i("ServerConnectionInfo", "Connecting...");

        List<String> fallbackAddresses = mServerConfig.getConnectionAddresses();
        if (!fallbackAddresses.isEmpty()) {
            mActiveAddressIndex = mActiveAddressIndex % fallbackAddresses.size();
            String activeAddress = fallbackAddresses.get(mActiveAddressIndex);
            Log.i("ServerConnectionInfo", "Trying endpoint " + (mActiveAddressIndex + 1) +
                    "/" + fallbackAddresses.size() + ": " + activeAddress);
            mConnectionRequest.setServerAddress(activeAddress, mServerConfig.port);
        }

        IRCConnection connection = null;
        boolean createdNewConnection = false;
        if (mApi == null || !(mApi instanceof IRCConnection)) {
            connection = new IRCConnection();
            ServerConfigManager configManager = ServerConfigManager.getInstance(mManager.getContext());
            connection.getServerConnectionData().setMessageStorageApi(new SQLiteMessageStorageApi(configManager.getServerChatLogDir(getUUID())));
            mSQLiteMiscStorage = new SQLiteMiscStorage(configManager.getServerMiscDataFile(getUUID()));
            connection.getServerConnectionData().setChannelDataStorage(new SQLiteChannelDataStorage(mSQLiteMiscStorage));
            connection.getServerConnectionData().getMessageFilterList().addMessageFilter(new IgnoreListMessageFilter(mServerConfig));
            if (mSASLOptions != null)
                connection.getServerConnectionData().getCapabilityManager().registerCapability(
                        new SASLCapability(mSASLOptions));
            connection.getServerConnectionData().getMessageFilterList().addMessageFilter(
                    new ZNCPlaybackMessageFilter(connection.getServerConnectionData()));
            MessageCommandHandler messageHandler = connection.getServerConnectionData()
                    .getCommandHandlerList().getHandler(MessageCommandHandler.class);
            DCCManager dccManager = DCCManager.getInstance(getConnectionManager().getContext());
            messageHandler.setDCCServerManager(dccManager.getServer());
            messageHandler.setDCCClientManager(dccManager.createClient(this));
            messageHandler.setCtcpVersionReply(mManager.getContext()
                    .getString(R.string.app_name), BuildConfig.VERSION_NAME, "Android");
            connection.getServerConnectionData().getCommandHandlerList()
                    .unregisterHandler(messageHandler);
            connection.getServerConnectionData().getCommandHandlerList()
                    .registerHandler(new ServiceMessageCommandHandler(messageHandler, this));
            KickCommandHandler kickHandler = connection.getServerConnectionData()
                    .getCommandHandlerList().getHandler(KickCommandHandler.class);
            if (kickHandler != null) {
                connection.getServerConnectionData().getCommandHandlerList()
                        .unregisterHandler(kickHandler);
                connection.getServerConnectionData().getCommandHandlerList()
                        .registerHandler(new SelfKickCommandHandler(kickHandler));
            }
            ServerConnectionData monitoredConnectionData = connection.getServerConnectionData();
            connection.getUserInfoApi().subscribeNickChanges((info, oldNick, newNick) -> {
                renameServiceNick(oldNick, newNick);
                renamePrivateConversation(oldNick, newNick);
                mMonitoredUsers.onNickChanged(monitoredConnectionData, oldNick, newNick);
            }, null, null);
            monitoredConnectionData.getCommandHandlerList().registerHandler(mMonitoredUsers);
            // Install the channel-mode snapshot wrapper before the network thread starts.
            // CommandHandlerList accepts only one handler per command, so registering it
            // later from the dialog would collide with ModeCommandHandler on numeric 324.
            ModeCommandHandler modeHandler = connection.getServerConnectionData()
                    .getCommandHandlerList().getHandler(ModeCommandHandler.class);
            if (modeHandler != null) {
                connection.getServerConnectionData().getCommandHandlerList()
                        .unregisterHandler(modeHandler);
                connection.getServerConnectionData().getCommandHandlerList()
                        .registerHandler(new ChannelModeSnapshotHandler(modeHandler));
            }
            if (mServerConfig.address != null &&
                    mServerConfig.address.toLowerCase(java.util.Locale.ROOT)
                            .contains("simosnap")) {
                connection.getServerConnectionData().getCommandHandlerList()
                        .registerHandler(new WhoXAccountHandler());
            }
            connection.addDisconnectListener((IRCConnection conn, Exception reason) -> {
                Log.w("ServerConnectionInfo", "IRC transport disconnected: " +
                        reason.getClass().getSimpleName() + ": " + reason.getMessage());
                notifyDisconnected();
            });
            createdNewConnection = true;
        } else {
            connection = (IRCConnection) mApi;
        }

        IRCConnection fConnection = connection;

        List<String> rejoinChannels = getChannels();

        connection.connect(mConnectionRequest, (Void v) -> {
            synchronized (this) {
                mConnecting = false;
                setConnected(true);
                mCurrentReconnectAttempt = 0;
                mEndpointFailoverAttempts = 0;
                mMonitoredUsers.synchronize(fConnection.getServerConnectionData());

                if (mServerConfig.execCommandsConnected != null) {
                    if (mAutoRunHelper == null)
                        mAutoRunHelper = new UserAutoRunCommandHelper(this);
                    mAutoRunHelper.executeUserCommands(mServerConfig.execCommandsConnected);
                }
            }

            List<String> joinChannels = new ArrayList<>();
            if (mServerConfig.autojoinChannels != null)
                joinChannels.addAll(mServerConfig.autojoinChannels);
            if (rejoinChannels != null && mServerConfig.rejoinChannels)
                joinChannels.addAll(rejoinChannels);
            if (joinChannels.size() > 0)
                fConnection.joinChannels(joinChannels, null, null);

        }, (Exception e) -> {
            if (e instanceof UserOverrideTrustManager.UserRejectedCertificateException ||
                    (e.getCause() != null && e.getCause() instanceof
                            UserOverrideTrustManager.UserRejectedCertificateException)) {
                Log.d("ServerConnectionInfo", "User rejected the certificate");
                synchronized (this) {
                    mUserDisconnectRequest = true;
                }
            }
            notifyDisconnected();
        });

        if (createdNewConnection) {
            setApi(connection);
        }
    }

    private void disconnect(boolean userExecutedQuit) {
        synchronized (this) {
            mUserDisconnectRequest = true;
            mReconnectHandler.removeCallbacks(mReconnectRunnable);
            if (!isConnected() && isConnecting()) {
                mConnecting = false;
                mDisconnecting = true;
                Thread disconnectThread = new Thread(() -> forceDisconnectSafely());
                disconnectThread.setName("Disconnect Thread");
                disconnectThread.start();
            } else if (isConnected()) {
                mDisconnecting = true;
                String message = AppSettings.getDefaultQuitMessage();
                if (userExecutedQuit) {
                    try {
                        ((IRCConnection) mApi).disconnect(null, null);
                    } catch (RuntimeException e) {
                        Log.w("ServerConnectionInfo", "Disconnect failed", e);
                        notifyFullyDisconnected();
                    }
                } else {
                    mApi.quit(message, null, (Exception e) -> {
                        forceDisconnectSafely();
                    });
                }
            } else {
                notifyFullyDisconnected();
            }
        }
    }

    private void forceDisconnectSafely() {
        ChatApi api = getApiInstance();
        if (!(api instanceof IRCConnection)) {
            notifyFullyDisconnected();
            return;
        }
        try {
            ((IRCConnection) api).disconnect(true);
        } catch (RuntimeException e) {
            // chatlib 0.3.3 can race with its connect thread and try to close a null socket.
            // Treat that state as already disconnected instead of crashing the process.
            Log.w("ServerConnectionInfo", "Forced disconnect failed", e);
            notifyFullyDisconnected();
        }
    }

    public void disconnect() {
        disconnect(false);
    }

    public void notifyUserExecutedQuit() {
        disconnect(true);
    }

    private void notifyDisconnected() {
        mMonitoredUsers.onDisconnected();
        synchronized (this) {
            // A failed socket can report both the connect error callback and the disconnect
            // listener. Process that failure once, otherwise two callbacks would skip an
            // endpoint or unexpectedly queue another reconnect after a user disconnect.
            if (!mConnected && !mConnecting && !mDisconnecting)
                return;
            if (mAutoRunHelper != null)
                mAutoRunHelper.cancelUserCommandExecution();
        }
        if (isDisconnecting()) {
            notifyFullyDisconnected();
            return;
        }
        synchronized (this) {
            setConnected(false);
            mConnecting = false;
            if (mDisconnecting) {
                notifyFullyDisconnected();
                return;
            }
            if (mUserDisconnectRequest)
                return;
        }
        List<String> fallbackAddresses = mServerConfig.getConnectionAddresses();
        if (fallbackAddresses.size() > 1) {
            mActiveAddressIndex = (mActiveAddressIndex + 1) % fallbackAddresses.size();
            if (mEndpointFailoverAttempts < fallbackAddresses.size() - 1) {
                mEndpointFailoverAttempts++;
                Log.i("ServerConnectionInfo", "Endpoint failed; trying fallback " +
                        (mActiveAddressIndex + 1) + "/" + fallbackAddresses.size() +
                        " immediately");
                mReconnectQueueTime = System.nanoTime();
                // Endpoint failover is part of this connection attempt and must not be gated by
                // the user's later automatic-reconnection preference.
                mReconnectHandler.post(this::connect);
                return;
            }
        }
        // Every configured endpoint has failed once. Only now apply the normal reconnect policy
        // before starting a new round from the next (normally primary) endpoint.
        mEndpointFailoverAttempts = 0;
        int reconnectDelay = mManager.getReconnectDelay(mCurrentReconnectAttempt++);
        if (reconnectDelay == -1)
            return;
        Log.i("ServerConnectionInfo", "Queuing reconnect in " + reconnectDelay + " ms");
        mReconnectQueueTime = System.nanoTime();
        mReconnectHandler.postDelayed(mReconnectRunnable, reconnectDelay);
    }

    private void notifyFullyDisconnected() {
        synchronized (this) {
            setConnected(false);
            mConnecting = false;
            mDisconnecting = false;
        }
        mManager.notifyConnectionFullyDisconnected(this);
    }

    public synchronized void close() {
        Log.i("ServerConnectionInfo", "Closing");
        if (getApiInstance() != null) {
            MessageStorageApi m = getApiInstance().getMessageStorageApi();
            if (m != null && m instanceof SQLiteMessageStorageApi)
                ((SQLiteMessageStorageApi) m).close();
            ServerConnectionData connectionData = ((ServerConnectionApi) getApiInstance())
                    .getServerConnectionData();
            connectionData.setMessageStorageApi(new StubMessageStorageApi());
            connectionData.setChannelDataStorage(null);
        }
        if (mSQLiteMiscStorage != null)
            mSQLiteMiscStorage.close();
    }

    public void notifyConnectivityChanged(boolean hasAnyConnectivity, boolean hasWifi) {
        mReconnectHandler.removeCallbacks(mReconnectRunnable);

        if (!hasAnyConnectivity || !AppSettings.isReconnectEnabled() ||
                (AppSettings.isReconnectWiFiOnly() && !hasWifi))
            return;
        if (AppSettings.isReconnectOnConnectivityChangeEnabled()) {
            connect(); // this will be ignored if we are already connected
        } else if (mReconnectQueueTime != -1L) {
            long reconnectDelay = mManager.getReconnectDelay(mCurrentReconnectAttempt++);
            if (reconnectDelay == -1)
                return;
            reconnectDelay = reconnectDelay - (System.nanoTime() - mReconnectQueueTime) / 1000000L;
            if (reconnectDelay <= 0L)
                connect();
            else
                mReconnectHandler.postDelayed(mReconnectRunnable, reconnectDelay);
        }
    }

    public UUID getUUID() {
        return mServerConfig.uuid;
    }

    public MonitoredUsersManager getMonitoredUsersManager() { return mMonitoredUsers; }

    public String getName() {
        return mServerConfig.name;
    }

    public boolean shouldHideJoinPartMessages() {
        return mServerConfig.shouldHideJoinPartMessages();
    }

    public String getServerAddress() {
        List<String> addresses = mServerConfig.getConnectionAddresses();
        return addresses.isEmpty() ? mServerConfig.address :
                addresses.get(Math.min(mActiveAddressIndex, addresses.size() - 1));
    }

    /** True when a private-query tab with this nickname already exists. */
    public boolean hasOpenConversation(String nick) {
        if (nick == null)
            return false;
        List<String> channels = getChannels();
        if (channels == null)
            return false;
        for (String channel : channels) {
            if (nick.equalsIgnoreCase(channel))
                return true;
        }
        return false;
    }

    public synchronized ChatApi getApiInstance() {
        return mApi;
    }

    public synchronized SQLiteMiscStorage getSQLiteMiscStorage() {
        return mSQLiteMiscStorage;
    }

    public MessageId.Parser getMessageIdParser() {
        // NOTE: We hardcode it to to SQLite here, as this is the only storage type we current use.
        // This might need to be changed if we switch storage type in the future.
        return SQLiteMessageStorageApi.getMessageIdParserInstance();
    }

    public boolean isConnected() {
        synchronized (this) {
            return mConnected;
        }
    }

    public void setConnected(boolean connected) {
        synchronized (this) {
            mConnected = connected;
        }
        notifyInfoChanged();
    }

    public boolean isConnecting() {
        synchronized (this) {
            return mConnecting;
        }
    }

    public boolean isDisconnecting() {
        synchronized (this) {
            return mDisconnecting;
        }
    }

    public boolean hasUserDisconnectRequest() {
        synchronized (this) {
            return mUserDisconnectRequest;
        }
    }

    public List<String> getChannels() {
        synchronized (this) {
            return mChannels;
        }
    }

    public synchronized void rememberServiceNick(String nick) {
        if (nick != null && !nick.trim().isEmpty())
            mServiceNicks.add(nick);
    }

    public synchronized boolean isKnownServiceNick(String nick) {
        if (nick == null)
            return false;
        for (String item : mServiceNicks) {
            if (item.equalsIgnoreCase(nick))
                return true;
        }
        return false;
    }

    public synchronized List<String> getServiceNicks() {
        return new ArrayList<>(mServiceNicks);
    }

    private synchronized void renameServiceNick(String oldNick, String newNick) {
        String found = null;
        for (String item : mServiceNicks) {
            if (item.equalsIgnoreCase(oldNick)) {
                found = item;
                break;
            }
        }
        if (found != null) {
            mServiceNicks.remove(found);
            rememberServiceNick(newNick);
        }
    }

    /** Follows a user's NICK change instead of opening a second private-query tab. */
    private void renamePrivateConversation(String oldNick, String newNick) {
        if (oldNick == null || newNick == null || oldNick.equalsIgnoreCase(newNick) ||
                !hasOpenConversation(oldNick))
            return;
        synchronized (this) {
            mQueryNickAliases.put(oldNick.toLowerCase(Locale.ROOT), newNick);
            // Preserve a chain when a user changes nick more than once in one connection.
            for (Map.Entry<String, String> alias : mQueryNickAliases.entrySet()) {
                if (alias.getValue().equalsIgnoreCase(oldNick))
                    alias.setValue(newNick);
            }
        }
        List<String> renamed;
        synchronized (this) {
            renamed = mChannels == null ? new ArrayList<>() : new ArrayList<>(mChannels);
        }
        boolean destinationExists = false;
        for (String channel : renamed) {
            if (channel.equalsIgnoreCase(newNick)) {
                destinationExists = true;
                break;
            }
        }
        for (int i = renamed.size() - 1; i >= 0; i--) {
            if (!renamed.get(i).equalsIgnoreCase(oldNick))
                continue;
            if (destinationExists)
                renamed.remove(i);
            else {
                renamed.set(i, newNick);
                destinationExists = true;
            }
        }
        mChatUIData.renameChannel(oldNick, newNick);
        MessageStorageApi storage = getApiInstance().getMessageStorageApi();
        if (storage instanceof SQLiteMessageStorageApi)
            ((SQLiteMessageStorageApi) storage).renameChannel(oldNick, newNick, null,
                    error -> Log.w("ServerConnectionInfo", "Unable to rename query history", error));
        mNotificationData.discardRenamedChannel(oldNick);
        setChannels(renamed);
    }

    /** Deliberately conservative: an IRCop alone is not treated as a service. */
    public boolean isTrustedService(String nick, String user, String host) {
        if (isKnownServiceNick(nick))
            return true;
        if (nick == null)
            return false;
        String n = nick.toLowerCase(Locale.ROOT);
        boolean serviceName = n.endsWith("serv") || n.equals("nickserv") ||
                n.equals("chanserv") || n.equals("memoserv") || n.equals("operserv") ||
                n.equals("hostserv") || n.equals("botserv") || n.equals("helpserv") ||
                n.equals("global");
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        String u = user == null ? "" : user.toLowerCase(Locale.ROOT);
        boolean serviceIdentity = h.contains("service") || h.contains("services") ||
                u.equals("services") || u.equals("service");
        return serviceName || serviceIdentity;
    }

    public boolean hasChannel(String channel) {
        synchronized (this) {
            for (String c : mChannels) {
                if (c.equalsIgnoreCase(channel))
                    return true;
            }
            return false;
        }
    }

    /** Adds a locally stored query to the visible tabs without requiring the nick to be online. */
    public void addStoredConversation(String channel) {
        if (channel == null || channel.isEmpty() || hasChannel(channel))
            return;
        List<String> channels;
        synchronized (this) {
            channels = mChannels == null ? new ArrayList<>() : new ArrayList<>(mChannels);
        }
        channels.add(channel);
        setChannels(channels);
    }

    /** Removes a locally opened query, such as a service conversation. */
    public void removeStoredConversation(String channel) {
        if (channel == null || channel.isEmpty())
            return;
        List<String> channels;
        synchronized (this) {
            channels = mChannels == null ? new ArrayList<>() : new ArrayList<>(mChannels);
        }
        boolean removed = false;
        for (int i = channels.size() - 1; i >= 0; --i) {
            if (channel.equalsIgnoreCase(channels.get(i))) {
                channels.remove(i);
                removed = true;
            }
        }
        if (removed)
            setChannels(channels);
    }

    /**
     * Closes a private query, including the chatlib entry that still uses an older nickname.
     *
     * The visible query is renamed when a user sends NICK, but chatlib 0.3.3 keeps the
     * original name in its joined-channel map. Calling leaveChannel with only the visible
     * name would therefore do nothing and leave the tab open.
     */
    public void closePrivateConversation(String channel, String reason) {
        if (channel == null || channel.isEmpty())
            return;
        // Locally opened queries are not necessarily present in chatlib's joined-channel map.
        // Remove the visible entry now; leaveChannel below still removes any backing chatlib entry.
        removeStoredConversation(channel);
        Map<String, String> aliases;
        synchronized (this) {
            aliases = new LinkedHashMap<>(mQueryNickAliases);
        }
        for (String target : PrivateConversationAliases.buildCloseTargets(aliases, channel))
            getApiInstance().leaveChannel(target, reason, null, null);
    }

    public void setChannels(List<String> channels) {
        // chatlib may emit a stale list immediately after NICK. Resolve aliases here so the
        // obsolete empty query cannot be recreated by that delayed callback.
        List<String> normalized = new ArrayList<>();
        synchronized (this) {
            for (String channel : channels) {
                String resolved = channel;
                String alias = channel == null ? null :
                        mQueryNickAliases.get(channel.toLowerCase(Locale.ROOT));
                if (alias != null)
                    resolved = alias;
                boolean duplicate = false;
                for (String existing : normalized) {
                    if (existing.equalsIgnoreCase(resolved)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate)
                    normalized.add(resolved);
            }
        }
        Collections.sort(normalized, String::compareToIgnoreCase);
        synchronized (this) {
            mChannels = normalized;
        }
        synchronized (mChannelsListeners) {
            mManager.notifyChannelListChanged(this, normalized);
            mManager.saveAutoconnectListAsync();
            List<ChannelListChangeListener> listeners = new ArrayList<>(mChannelsListeners);
            for (ChannelListChangeListener listener : listeners)
                listener.onChannelListChanged(this, normalized);
        }
    }

    public boolean isExpandedInDrawer() {
        synchronized (this) {
            return mExpandedInDrawer;
        }
    }

    public void setExpandedInDrawer(boolean expanded) {
        synchronized (this) {
            mExpandedInDrawer = expanded;
        }
    }

    public NotificationManager.ConnectionManager getNotificationManager() {
        return mNotificationData;
    }

    public String getUserNick() {
        return ((ServerConnectionApi) getApiInstance()).getServerConnectionData().getUserNick();
    }

    public ChatUIData getChatUIData() {
        return mChatUIData;
    }

    public void addOnChannelInfoChangeListener(InfoChangeListener listener) {
        synchronized (mInfoListeners) {
            mInfoListeners.add(listener);
        }
    }

    public void removeOnChannelInfoChangeListener(InfoChangeListener listener) {
        synchronized (mInfoListeners) {
            mInfoListeners.remove(listener);
        }
    }

    public void addOnChannelListChangeListener(ChannelListChangeListener listener) {
        synchronized (mChannelsListeners) {
            mChannelsListeners.add(listener);
        }
    }

    public void removeOnChannelListChangeListener(ChannelListChangeListener listener) {
        synchronized (mChannelsListeners) {
            mChannelsListeners.remove(listener);
        }
    }

    private void notifyInfoChanged() {
        synchronized (mInfoListeners) {
            for (InfoChangeListener listener : mInfoListeners)
                listener.onConnectionInfoChanged(this);
            mManager.notifyConnectionInfoChanged(this);
        }
    }

    private Runnable mReconnectRunnable = () -> {
        mReconnectQueueTime = -1L;
        if (!AppSettings.isReconnectEnabled() || (AppSettings.isReconnectWiFiOnly() &&
                !ServerConnectionManager.isWifiConnected(mManager.getContext())))
            return;
        this.connect();
    };

    public interface InfoChangeListener {
        void onConnectionInfoChanged(ServerConnectionInfo connection);
    }

    public interface ChannelListChangeListener {
        void onChannelListChanged(ServerConnectionInfo connection, List<String> newChannels);
    }

}
