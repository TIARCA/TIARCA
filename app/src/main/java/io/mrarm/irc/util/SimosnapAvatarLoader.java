package io.mrarm.irc.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.mrarm.irc.R;

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
    private static final long MISSING_TTL_MS = 5 * 60_000L;
    /**
     * Negative cache only for confirmed HTTP 404s. Transient network/HTTP/decode failures are
     * never remembered, so a temporary failure cannot hide an avatar for the whole app process.
     */
    private static final Map<String, Long> MISSING =
            new LinkedHashMap<String, Long>(64, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > MAX_MISSING_ENTRIES;
                }
            };
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4096) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    private SimosnapAvatarLoader() { }

    public static void load(ImageView view, String account, boolean large, Callback callback) {
        String[] urls = getCandidateUrls(account, large);
        if (urls.length == 0) {
            clear(view, callback);
            return;
        }

        String requestKey = urls[0];
        view.setTag(R.id.tag_simosnap_avatar_url, requestKey);
        view.setImageDrawable(null);
        view.setVisibility(View.GONE);

        Bitmap cached = findCached(urls);
        if (cached != null) {
            showIfCurrent(view, requestKey, cached, callback);
            return;
        }

        EXECUTOR.execute(() -> {
            Bitmap bitmap = null;
            for (String url : urls) {
                Bitmap cachedCandidate = CACHE.get(url);
                if (cachedCandidate != null) {
                    bitmap = cachedCandidate;
                    break;
                }
                if (isRecentlyMissing(url))
                    continue;

                DownloadResult result = download(url);
                if (result.bitmap != null) {
                    CACHE.put(url, result.bitmap);
                    bitmap = result.bitmap;
                    break;
                }
                if (result.notFound)
                    rememberMissing(url);
            }
            Bitmap resolved = bitmap;
            MAIN.post(() -> showIfCurrent(view, requestKey, resolved, callback));
        });
    }

    static String[] getCandidateUrls(String account, boolean large) {
        if (account == null || account.trim().isEmpty())
            return new String[0];
        String hash = md5(account);
        if (hash == null)
            return new String[0];
        String small = SMALL_BASE + hash + ".png";
        if (!large)
            return new String[] { small };
        return new String[] { LARGE_BASE + hash + ".png", small };
    }

    private static Bitmap findCached(String[] urls) {
        for (String url : urls) {
            Bitmap bitmap = CACHE.get(url);
            if (bitmap != null)
                return bitmap;
        }
        return null;
    }

    private static boolean isRecentlyMissing(String url) {
        long now = SystemClock.elapsedRealtime();
        synchronized (MISSING) {
            Long recorded = MISSING.get(url);
            if (recorded == null)
                return false;
            if (now - recorded >= MISSING_TTL_MS) {
                MISSING.remove(url);
                return false;
            }
            return true;
        }
    }

    private static void rememberMissing(String url) {
        synchronized (MISSING) {
            MISSING.put(url, SystemClock.elapsedRealtime());
        }
    }

    public static void clear(ImageView view, Callback callback) {
        view.setTag(R.id.tag_simosnap_avatar_url, null);
        view.setImageDrawable(null);
        view.setVisibility(View.GONE);
        if (callback != null)
            callback.onResult(false);
    }

    private static void showIfCurrent(ImageView view, String requestKey, Bitmap bitmap,
                                      Callback callback) {
        if (!requestKey.equals(view.getTag(R.id.tag_simosnap_avatar_url)))
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

    private static DownloadResult download(String value) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(value).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "image/png,image/*");
            int status = connection.getResponseCode();
            if (isPermanentMissingStatus(status))
                return DownloadResult.notFound();
            if (status != HttpURLConnection.HTTP_OK)
                return DownloadResult.failed();

            String type = connection.getContentType();
            if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("image/"))
                return DownloadResult.failed();
            try (InputStream stream = connection.getInputStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                return bitmap == null ? DownloadResult.failed() : DownloadResult.loaded(bitmap);
            }
        } catch (Exception ignored) {
            return DownloadResult.failed();
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    static boolean isPermanentMissingStatus(int status) {
        return status == HttpURLConnection.HTTP_NOT_FOUND;
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

    private static final class DownloadResult {
        final Bitmap bitmap;
        final boolean notFound;

        private DownloadResult(Bitmap bitmap, boolean notFound) {
            this.bitmap = bitmap;
            this.notFound = notFound;
        }

        static DownloadResult loaded(Bitmap bitmap) {
            return new DownloadResult(bitmap, false);
        }

        static DownloadResult notFound() {
            return new DownloadResult(null, true);
        }

        static DownloadResult failed() {
            return new DownloadResult(null, false);
        }
    }
}
