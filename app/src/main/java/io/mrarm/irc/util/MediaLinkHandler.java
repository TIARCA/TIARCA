package io.mrarm.irc.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.mrarm.irc.MediaViewerActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.config.SharingSettings;

/** Detects remote media by HTTP content type and opens it inside the app. */
public final class MediaLinkHandler {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MediaLinkHandler() { }

    public static void open(Activity activity, String address) {
        if (!SharingSettings.internalViewerEnabled(activity)) {
            openExternal(activity, address);
            return;
        }
        Uri uri = Uri.parse(address);
        if (!("https".equalsIgnoreCase(uri.getScheme()) ||
                "http".equalsIgnoreCase(uri.getScheme()))) {
            openExternal(activity, address);
            return;
        }
        Toast.makeText(activity, R.string.media_checking, Toast.LENGTH_SHORT).show();
        EXECUTOR.execute(() -> {
            MediaTarget target = detectTarget(address);
            MAIN.post(() -> {
                if (target != null && (target.type.startsWith("image/") ||
                        target.type.startsWith("audio/") || target.type.startsWith("video/"))) {
                    Intent intent = new Intent(activity, MediaViewerActivity.class);
                    intent.putExtra(MediaViewerActivity.ARG_URL, target.url);
                    intent.putExtra(MediaViewerActivity.ARG_MIME, target.type);
                    activity.startActivity(intent);
                } else {
                    openExternal(activity, address);
                }
            });
        });
    }

    private static MediaTarget detectTarget(String address) {
        MediaTarget simosnap = resolveSimosnap(address);
        if (simosnap != null)
            return simosnap;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "RevolutionIRC/0.5.5");
            int status = connection.getResponseCode();
            if (status >= 200 && status < 400) {
                String type = connection.getContentType();
                if (type != null) return new MediaTarget(address,
                        type.split(";", 2)[0].toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        String lower = address.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\.(jpg|jpeg|png|gif|webp)([?#].*)?$"))
            return new MediaTarget(address, "image/*");
        if (lower.matches(".*\\.(mp3|m4a|aac|ogg|wav)([?#].*)?$"))
            return new MediaTarget(address, "audio/*");
        if (lower.matches(".*\\.(mp4|webm|mkv|mov)([?#].*)?$"))
            return new MediaTarget(address, "video/*");
        return null;
    }

    /** Resolve a Simosnap viewer page to its actual TUS media resource. */
    private static MediaTarget resolveSimosnap(String address) {
        HttpURLConnection connection = null;
        try {
            Uri uri = Uri.parse(address);
            if (!"media.simosnap.com".equalsIgnoreCase(uri.getHost()))
                return null;
            String path = uri.getPath();
            if (path == null)
                return null;
            String id;
            if (path.matches("/files/[A-Fa-f0-9]{32}/?"))
                id = path.replaceFirst("^/files/", "").replace("/", "");
            else if (path.matches("/[A-Fa-f0-9]{32}/?"))
                id = path.replace("/", "");
            else
                return null;
            String direct = "https://media.simosnap.com/" + id;
            connection = (HttpURLConnection) new URL(direct).openConnection();
            // A normal HEAD response from the TUS endpoint omits Content-Type. A one-byte
            // range request returns the stored media type without downloading the file.
            connection.setRequestProperty("Range", "bytes=0-0");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setRequestProperty("User-Agent", "RevolutionIRC/0.5.5");
            int status = connection.getResponseCode();
            if (status == 200 || status == 206) {
                String type = connection.getContentType();
                if (type != null)
                    return new MediaTarget(direct,
                            type.split(";", 2)[0].toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null)
                connection.disconnect();
        }
        return null;
    }

    private static class MediaTarget {
        final String url;
        final String type;
        MediaTarget(String url, String type) {
            this.url = url;
            this.type = type;
        }
    }

    private static void openExternal(Activity activity, String address) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(address)));
        } catch (Exception error) {
            Toast.makeText(activity, R.string.media_open_failed, Toast.LENGTH_LONG).show();
        }
    }
}
