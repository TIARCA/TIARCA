package io.mrarm.irc;

import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.irc.MonitoredUsersManager;

/** Posts only confirmed MONITOR transitions; initial synchronization is explicitly ignored. */
final class MonitoredUsersNotificationManager implements MonitoredUsersManager.Listener {
    private static final String CHANNEL_ID = "monitored_users";
    private static final int NOTIFICATION_ID_BASE = 25000;
    private final Context context;
    private final ServerConnectionInfo connection;

    MonitoredUsersNotificationManager(Context context, ServerConnectionInfo connection) {
        this.context = context.getApplicationContext();
        this.connection = connection;
        createChannel();
    }

    @Override public void onPresenceUpdated(ServerConfigData.MonitoredUser user,
                                            MonitoredUsersManager.PresenceUpdate update) {
        if (update == MonitoredUsersManager.PresenceUpdate.INITIAL_STATE ||
                (update == MonitoredUsersManager.PresenceUpdate.BECAME_ONLINE && !user.notifyOnline) ||
                (update == MonitoredUsersManager.PresenceUpdate.BECAME_OFFLINE && !user.notifyOffline))
            return;
        String nick = user.currentNick == null ? user.nick : user.currentNick;
        if (nick == null) return;
        PendingIntent intent = PendingIntent.getActivity(context, notificationId(user),
                MainActivity.getLaunchIntent(context, connection, nick),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int text = update == MonitoredUsersManager.PresenceUpdate.BECAME_ONLINE
                ? R.string.monitor_notification_online : R.string.monitor_notification_offline;
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_tiarca)
                .setContentTitle(nick)
                .setContentText(context.getString(text, nick))
                .setContentIntent(intent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.colorNotificationMention));
        NotificationManager.postNotification(context, notificationId(user), notification.build());
    }

    @Override public void onSyncStateChanged(MonitoredUsersManager.SyncState state) { }

    private int notificationId(ServerConfigData.MonitoredUser user) {
        return NOTIFICATION_ID_BASE + ((connection.getUUID().toString() + ":" + user.nick).hashCode()
                & 0x7fffffff) % 100000;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.monitor_notification_channel),
                android.app.NotificationManager.IMPORTANCE_DEFAULT);
        channel.setGroup(NotificationManager.getSystemNotificationChannelGroup(context));
        android.app.NotificationManager manager = (android.app.NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }
}
