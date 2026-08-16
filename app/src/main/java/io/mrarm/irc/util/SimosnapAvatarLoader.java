package io.mrarm.irc.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads the public Simosnap account avatars without adding an image-loading dependency. */
public final class SimosnapAvatarLoader {

    public interface Callback { void onResult(boolean loaded); }

    private static final String SMALL_BASE =
            "https://www.simosnap.org/uploads/avatars/40/";
    private static final String LARGE_BASE =
            "https://www.simosnap.org/uploads/avatars/default/";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final int MAX_MISSING_ENTRIES = 1024;
    private static final Set<String> MISSING =
            Collections.synchronizedSet(new LinkedHashSet<>());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4096) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    private SimosnapAvatarLoader() { }

    public static void load(ImageView view, String account, boolean large, Callback callback) {
        if (account == null || account.trim().isEmpty()) {
            clear(view, callback);
            return;
        }
        String hash = md5(account);
        if (hash == null) {
            clear(view, callback);
            return;
        }
        String url = (large ? LARGE_BASE : SMALL_BASE) + hash + ".png";
        view.setTag(url);
        view.setImageDrawable(null);
        view.setVisibility(View.GONE);
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            showIfCurrent(view, url, cached, callback);
            return;
        }
        if (MISSING.contains(url)) {
            if (callback != null)
                callback.onResult(false);
            return;
        }
        EXECUTOR.execute(() -> {
            Bitmap bitmap = download(url);
            if (bitmap != null)
                CACHE.put(url, bitmap);
            else
                rememberMissing(url);
            MAIN.post(() -> showIfCurrent(view, url, bitmap, callback));
        });
    }

    private static void rememberMissing(String url) {
        synchronized (MISSING) {
            MISSING.add(url);
            while (MISSING.size() > MAX_MISSING_ENTRIES)
                MISSING.remove(MISSING.iterator().next());
        }
    }

    public static void clear(ImageView view, Callback callback) {
        view.setTag(null);
        view.setImageDrawable(null);
        view.setVisibility(View.GONE);
        if (callback != null)
            callback.onResult(false);
    }

    private static void showIfCurrent(ImageView view, String url, Bitmap bitmap,
                                      Callback callback) {
        if (!url.equals(view.getTag()))
            return;
        if (bitmap != null) {
            view.setImageBitmap(bitmap);
            view.setVisibility(View.VISIBLE);
        } else {
            view.setImageDrawable(null);
            view.setVisibility(View.GONE);
        }
        if (callback != null)
            callback.onResult(bitmap != null);
    }

    private static Bitmap download(String value) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(value).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "image/png,image/*");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                return null;
            String type = connection.getContentType();
            if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("image/"))
                return null;
            try (InputStream stream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(stream);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    private static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] result = digest.digest(value.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder(32);
            for (byte b : result)
                out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception ignored) {
            return null;
        }
    }
}
