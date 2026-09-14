package io.mrarm.irc;

import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Map;

import io.mrarm.chatlib.dto.MessageId;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.message.MessageListener;
import io.mrarm.irc.job.ServerPingScheduler;
import io.mrarm.irc.util.DiagnosticLog;
import io.mrarm.irc.util.WarningHelper;

public class IRCService extends Service implements ServerConnectionManager.ConnectionsListener {

    public static final int IDLE_NOTIFICATION_ID = 100;
    public static final int EXIT_ACTION_ID = 102; // 101 is taken by chat summary
    public static final String ACTION_START_FOREGROUND = "start_foreground";

    private static final String IDLE_NOTIFICATION_CHANNEL = "IdleNotification";

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private ConnectivityManager mConnectivityManager;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private boolean mConnectivityMonitoringStarted;
    private Boolean mLastInternetConnectivity;
    private Boolean mLastWifiConnectivity;

    private final Runnable mConnectivityChangedRunnable = () -> {
        if (!mConnectivityMonitoringStarted)
            return;
        Network activeNetwork = mConnectivityManager == null ? null :
                mConnectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = activeNetwork == null ? null :
                mConnectivityManager.getNetworkCapabilities(activeNetwork);
        boolean connected = hasInternetConnectivity();
        boolean wifi = ServerConnectionManager.isWifiConnected(this);
        DiagnosticLog.d(this, "NETWORK", () -> "Active network connected=" + connected +
                ", validated=" + hasCapability(capabilities,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED) + ", wifi=" + wifi +
                ", cellular=" + hasTransport(capabilities,
                NetworkCapabilities.TRANSPORT_CELLULAR) + ", vpn=" +
                hasTransport(capabilities, NetworkCapabilities.TRANSPORT_VPN));
        if (mLastInternetConnectivity != null && mLastInternetConnectivity == connected &&
                mLastWifiConnectivity != null && mLastWifiConnectivity == wifi)
            return;
        mLastInternetConnectivity = connected;
        mLastWifiConnectivity = wifi;
        DiagnosticLog.i(this, "NETWORK", () -> "Connectivity changed connected=" + connected +
                ", wifi=" + wifi);
        ServerConnectionManager.getInstance(this).notifyConnectivityChanged(connected);
        ServerPingScheduler.getInstance(this).onWifiStateChanged(wifi);
    };

    private boolean mCreatedChannel = false;

    private final Map<ServerConnectionInfo, MessageListener> messageListeners = new HashMap<>();
    private final Map<ServerConnectionInfo, ServerConnectionInfo.InfoChangeListener> stateListeners =
            new HashMap<>();
    private final Map<ServerConnectionInfo, IRCConnection.DisconnectListener> disconnectListeners =
            new HashMap<>();

    public static void start(Context context) {
        Intent intent = new Intent(context, IRCService.class);
        intent.setAction(ACTION_START_FOREGROUND);
        context.startForegroundService(intent);
    }
    public static void stop(Context context) {
        context.stopService(new Intent(context, IRCService.class));
    }

    public static void createNotificationChannel(Context ctx) {
        NotificationChannel channel = new NotificationChannel(IDLE_NOTIFICATION_CHANNEL,
                ctx.getString(R.string.notification_channel_idle),
                android.app.NotificationManager.IMPORTANCE_MIN);
        channel.setGroup(NotificationManager.getSystemNotificationChannelGroup(ctx));
        channel.setShowBadge(false);
        android.app.NotificationManager mgr = (android.app.NotificationManager)
                ctx.getSystemService(NOTIFICATION_SERVICE);
        mgr.createNotificationChannel(channel);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DiagnosticLog.i(this, "SERVICE", () -> "IRC service created");

        WarningHelper.setAppContext(getApplicationContext());

        ChatLogStorageManager.getInstance(getApplicationContext());

        for (ServerConnectionInfo connection : ServerConnectionManager.getInstance(this).getConnections())
            onConnectionAdded(connection);
        ServerConnectionManager.getInstance(this).addListener(this);

        startConnectivityMonitoring();

        ServerPingScheduler.getInstance(this).startIfEnabled();
    }

    @Override
    public void onDestroy() {
        DiagnosticLog.i(this, "SERVICE", () -> "IRC service destroyed");
        super.onDestroy();

        if (ServerConnectionManager.hasInstance()) {
            for (ServerConnectionInfo connection : ServerConnectionManager.getInstance(this)
                    .getConnections())
                onConnectionRemoved(connection);
            ServerConnectionManager.getInstance(this).removeListener(this);
        }

        stopConnectivityMonitoring();

        ServerPingScheduler.getInstance(this).stop();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        DiagnosticLog.d(this, "SERVICE", () -> "Service start action=" + action +
                ", flags=" + flags + ", startId=" + startId);
        if (action == null)
            return START_STICKY;
        if (action.equals(ACTION_START_FOREGROUND)) {
            if (!mCreatedChannel) {
                createNotificationChannel(this);
                mCreatedChannel = true;
            }

            StringBuilder b = new StringBuilder();
            int connectedCount = 0, connectingCount = 0, disconnectedCount = 0;
            for (ServerConnectionInfo connectionInfo :
                    ServerConnectionManager.getInstance(this).getConnections()) {
                if (connectionInfo.isConnected())
                    connectedCount++;
                else if (connectionInfo.isConnecting())
                    connectingCount++;
                else
                    disconnectedCount++;
            }
            b.append(getResources().getQuantityString(R.plurals.service_status_connected, connectedCount, connectedCount));
            if (connectingCount > 0) {
                b.append(getResources().getString(R.string.text_comma));
                b.append(getResources().getQuantityString(R.plurals.service_status_connecting, connectingCount, connectingCount));
            }
            if (disconnectedCount > 0) {
                b.append(getResources().getString(R.string.text_comma));
                b.append(getResources().getQuantityString(R.plurals.service_status_disconnected, disconnectedCount, disconnectedCount));
            }

            Intent mainIntent = MainActivity.getLaunchIntent(this, null, null);
            PendingIntent exitIntent = PendingIntent.getBroadcast(this, EXIT_ACTION_ID,
                    ExitActionReceiver.getIntent(this),
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder notification = new NotificationCompat.Builder(this, IDLE_NOTIFICATION_CHANNEL)
                    .setContentTitle(getString(R.string.service_title))
                    .setContentText(b.toString())
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(PendingIntent.getActivity(this, IDLE_NOTIFICATION_ID, mainIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                    .addAction(R.drawable.ic_close, getString(R.string.action_exit), exitIntent);
            notification.setSmallIcon(R.drawable.ic_notification_tiarca);
            // REMOTE_MESSAGING was introduced in Android 14. Older releases accept the
            // ordinary foreground-service call but do not know this service type.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(IDLE_NOTIFICATION_ID, notification.build(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
            } else {
                startForeground(IDLE_NOTIFICATION_ID, notification.build());
            }
            final int finalConnectedCount = connectedCount;
            final int finalConnectingCount = connectingCount;
            final int finalDisconnectedCount = disconnectedCount;
            DiagnosticLog.d(this, "SERVICE", () -> "Foreground active connected=" +
                    finalConnectedCount + ", connecting=" + finalConnectingCount +
                    ", disconnected=" + finalDisconnectedCount);
        }
        return START_STICKY;
    }

    private void onMessage(ServerConnectionInfo connection, String channel, MessageInfo info,
                           MessageId messageId) {
        NotificationManager.getInstance().processMessage(this, connection, channel, info, messageId);
        ChatLogStorageManager.getInstance(this).onMessage(connection);
    }

    @Override
    public void onConnectionAdded(ServerConnectionInfo connection) {
        MessageListener listener =  (String channel, MessageInfo info, MessageId id) -> {
            onMessage(connection, channel, info, id);
        };
        messageListeners.put(connection, listener);
        connection.getApiInstance().getMessageStorageApi().subscribeChannelMessages(null, listener, null, null);

        String serverId = DiagnosticLog.pseudonym("server", connection.getUUID().toString());
        ServerConnectionInfo.InfoChangeListener stateListener = changed ->
                DiagnosticLog.i(this, "CONNECTION", () -> serverId + " connected=" +
                        changed.isConnected() + ", connecting=" + changed.isConnecting() +
                        ", disconnecting=" + changed.isDisconnecting() +
                        ", userDisconnect=" + changed.hasUserDisconnectRequest());
        stateListeners.put(connection, stateListener);
        connection.addOnChannelInfoChangeListener(stateListener);

        if (connection.getApiInstance() instanceof IRCConnection) {
            IRCConnection ircConnection = (IRCConnection) connection.getApiInstance();
            IRCConnection.DisconnectListener disconnectListener = (ignored, reason) -> {
                String reasonClass = reason == null ? "unknown" :
                        reason.getClass().getSimpleName();
                DiagnosticLog.w(this, "CONNECTION",
                        () -> serverId + " transport disconnected reason=" + reasonClass,
                        reason);
            };
            disconnectListeners.put(connection, disconnectListener);
            ircConnection.addDisconnectListener(disconnectListener);
        }
        DiagnosticLog.i(this, "CONNECTION", () -> serverId + " added to service");
    }

    @Override
    public void onConnectionRemoved(ServerConnectionInfo connection) {
        MessageListener listener = messageListeners.remove(connection);
        if (listener != null)
            connection.getApiInstance().getMessageStorageApi().unsubscribeChannelMessages(null, listener, null, null);
        ServerConnectionInfo.InfoChangeListener stateListener = stateListeners.remove(connection);
        if (stateListener != null)
            connection.removeOnChannelInfoChangeListener(stateListener);
        IRCConnection.DisconnectListener disconnectListener = disconnectListeners.remove(connection);
        if (disconnectListener != null && connection.getApiInstance() instanceof IRCConnection)
            ((IRCConnection) connection.getApiInstance()).removeDisconnectListener(disconnectListener);
        String serverId = DiagnosticLog.pseudonym("server", connection.getUUID().toString());
        DiagnosticLog.i(this, "CONNECTION", () -> serverId + " removed from service");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static class BootReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
                return;
            DiagnosticLog.i(context, "SERVICE", () -> "Device boot receiver started IRC service");
            IRCService.start(context);
        }

    }

    public static class ExitActionReceiver extends BroadcastReceiver {

        public static Intent getIntent(Context context) {
            return new Intent(context, ExitActionReceiver.class);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            ((IRCApplication) context.getApplicationContext()).requestExit();
        }

    }

    private void startConnectivityMonitoring() {
        mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        mConnectivityMonitoringStarted = true;
        mLastInternetConnectivity = null;
        mLastWifiConnectivity = null;
        if (mConnectivityManager != null) {
            mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    DiagnosticLog.d(IRCService.this, "NETWORK", () -> "Network became available");
                    scheduleConnectivityChanged();
                }

                @Override
                public void onLost(Network network) {
                    DiagnosticLog.d(IRCService.this, "NETWORK", () -> "Network was lost");
                    // Re-read the active network: another transport may already have replaced it.
                    scheduleConnectivityChanged();
                }

                @Override
                public void onCapabilitiesChanged(Network network,
                                                  NetworkCapabilities capabilities) {
                    DiagnosticLog.d(IRCService.this, "NETWORK", () ->
                            "Capabilities changed internet=" + hasCapability(capabilities,
                            NetworkCapabilities.NET_CAPABILITY_INTERNET) + ", validated=" +
                            hasCapability(capabilities,
                            NetworkCapabilities.NET_CAPABILITY_VALIDATED) + ", wifi=" +
                            hasTransport(capabilities, NetworkCapabilities.TRANSPORT_WIFI) +
                            ", cellular=" + hasTransport(capabilities,
                            NetworkCapabilities.TRANSPORT_CELLULAR));
                    scheduleConnectivityChanged();
                }
            };
            mConnectivityManager.registerDefaultNetworkCallback(mNetworkCallback);
        }
        scheduleConnectivityChanged();
    }

    private void stopConnectivityMonitoring() {
        mConnectivityMonitoringStarted = false;
        mMainHandler.removeCallbacks(mConnectivityChangedRunnable);
        if (mConnectivityManager != null && mNetworkCallback != null) {
            try {
                mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            } catch (IllegalArgumentException ignored) {
                // The callback may already have been removed while the service was stopping.
            }
            mNetworkCallback = null;
        }
    }

    private void scheduleConnectivityChanged() {
        mMainHandler.removeCallbacks(mConnectivityChangedRunnable);
        mMainHandler.postDelayed(mConnectivityChangedRunnable, 150L);
    }

    private boolean hasInternetConnectivity() {
        if (mConnectivityManager == null)
            return false;
        Network activeNetwork = mConnectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = activeNetwork == null ? null :
                mConnectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private static boolean hasCapability(NetworkCapabilities capabilities, int capability) {
        return capabilities != null && capabilities.hasCapability(capability);
    }

    private static boolean hasTransport(NetworkCapabilities capabilities, int transport) {
        return capabilities != null && capabilities.hasTransport(transport);
    }

}
