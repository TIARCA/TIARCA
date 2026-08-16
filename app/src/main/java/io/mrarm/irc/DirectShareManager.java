package io.mrarm.irc;

import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Publishes recent IRC conversations to the Android Direct Share surface. */
public final class DirectShareManager {

    private static final String TAG = "DirectShareManager";
    private static final String CATEGORY = "io.tiarca.irc.category.TEXT_SHARE_TARGET";
    private static final String LEGACY_ID_PREFIX = "irc-share|";
    private static final String ID_PREFIX = "irc-share-v2|";
    private static final String ID_SEPARATOR = "|";
    private static boolean sLegacyShortcutsRemoved;

    private DirectShareManager() {
    }

    /** Performs one-time shortcut maintenance even before a conversation is opened. */
    public static void initialize(Context context) {
        removeLegacyShortcuts(context);
        refreshShortcutLabels(context);
    }

    public static void publishConversation(Context context, ServerConnectionInfo server,
                                           String channel) {
        if (context == null || server == null || channel == null || channel.isEmpty())
            return;
        removeLegacyShortcuts(context);
        String id = createId(server.getUUID(), channel);
        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.putExtra(MainActivity.ARG_SERVER_UUID, server.getUUID().toString());
        launchIntent.putExtra(MainActivity.ARG_CHANNEL_NAME, channel);
        launchIntent.setAction(Intent.ACTION_VIEW);
        HashSet<String> categories = new HashSet<>();
        categories.add(CATEGORY);
        try {
            ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(context, id)
                    .setShortLabel(channel)
                    .setLongLabel(channel + " — " + server.getName())
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_direct_share))
                    .setIntent(launchIntent)
                    .setCategories(categories)
                    .setLongLived(true)
                    .addCapabilityBinding("actions.intent.SEND_MESSAGE")
                    .build();
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut);
        } catch (RuntimeException e) {
            // Direct Share is optional and must never make opening a chat fail.
            Log.e(TAG, "Unable to publish Direct Share shortcut", e);
        }
    }

    private static synchronized void removeLegacyShortcuts(Context context) {
        if (sLegacyShortcutsRemoved)
            return;
        List<String> legacyIds = new ArrayList<>();
        for (ShortcutInfoCompat shortcut : ShortcutManagerCompat.getDynamicShortcuts(context)) {
            if (shortcut.getId().startsWith(LEGACY_ID_PREFIX))
                legacyIds.add(shortcut.getId());
        }
        if (!legacyIds.isEmpty()) {
            ShortcutManagerCompat.removeLongLivedShortcuts(context, legacyIds);
            ShortcutManagerCompat.removeDynamicShortcuts(context, legacyIds);
        }
        sLegacyShortcutsRemoved = true;
    }

    private static void refreshShortcutLabels(Context context) {
        List<ShortcutInfoCompat> updates = new ArrayList<>();
        for (ShortcutInfoCompat existing : ShortcutManagerCompat.getDynamicShortcuts(context)) {
            if (!existing.getId().startsWith(ID_PREFIX))
                continue;
            String channel = String.valueOf(existing.getShortLabel());
            String oldLongLabel = String.valueOf(existing.getLongLabel());
            String suffix = " — " + channel;
            if (!oldLongLabel.endsWith(suffix))
                continue;
            String network = oldLongLabel.substring(0, oldLongLabel.length() - suffix.length());
            HashSet<String> categories = existing.getCategories() == null
                    ? new HashSet<>() : new HashSet<>(existing.getCategories());
            categories.add(CATEGORY);
            ShortcutInfoCompat.Builder builder = new ShortcutInfoCompat.Builder(context,
                    existing.getId())
                    .setShortLabel(channel)
                    .setLongLabel(channel + " — " + network)
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_direct_share))
                    .setIntent(existing.getIntent())
                    .setCategories(categories)
                    .setRank(existing.getRank())
                    .setLongLived(true)
                    .addCapabilityBinding("actions.intent.SEND_MESSAGE");
            updates.add(builder.build());
        }
        if (updates.isEmpty())
            return;
        try {
            ShortcutManagerCompat.updateShortcuts(context, updates);
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to refresh Direct Share shortcut labels", e);
        }
    }

    /** Adds the IRC destination encoded in a Sharesheet shortcut to the received intent. */
    public static void applyTarget(Intent intent) {
        if (intent == null || intent.hasExtra(MainActivity.ARG_SERVER_UUID))
            return;
        Target target = parseId(intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID));
        if (target == null)
            return;
        intent.putExtra(MainActivity.ARG_SERVER_UUID, target.server.toString());
        intent.putExtra(MainActivity.ARG_CHANNEL_NAME, target.channel);
    }

    private static String createId(UUID server, String channel) {
        String encodedChannel = Base64.encodeToString(channel.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return ID_PREFIX + server + ID_SEPARATOR + encodedChannel;
    }

    private static Target parseId(String id) {
        if (id == null || !id.startsWith(ID_PREFIX))
            return null;
        int separator = id.indexOf(ID_SEPARATOR, ID_PREFIX.length());
        if (separator < 0)
            return null;
        try {
            UUID server = UUID.fromString(id.substring(ID_PREFIX.length(), separator));
            byte[] channelBytes = Base64.decode(id.substring(separator + 1),
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            String channel = new String(channelBytes, StandardCharsets.UTF_8);
            return channel.isEmpty() ? null : new Target(server, channel);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static final class Target {
        final UUID server;
        final String channel;

        Target(UUID server, String channel) {
            this.server = server;
            this.channel = channel;
        }
    }
}
