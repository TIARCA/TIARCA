package io.mrarm.irc.util;

import android.content.Context;
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
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;

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

    public static void load(ImageView view, ServerConnectionInfo connection, String account,
                            boolean large, Callback callback) {
        // Check before URL construction and cache lookup, not just before downloading.
        if (!SimosnapAvatarManager.isSupported(connection)) {
            clear(view, callback);
            return;
        }
        String[] urls = getCandidateUrls(account, large);
        if (urls.length == 0) {
            clear(view, callback);
            return;
        }

        String requestKey = urls[0];
        Context diagnosticContext = view.getContext().getApplicationContext();
        String accountId = DiagnosticLog.pseudonym("account", account);
        DiagnosticLog.d(diagnosticContext, "AVATAR", () ->
                "request account=" + accountId +
                        ", requested=" + (large ? "large" : "small") +
                        ", candidates=" + (large ? "default->40" : "40"));
        view.setTag(R.id.tag_simosnap_avatar_url, requestKey);
        view.setImageDrawable(null);
        view.setVisibility(View.GONE);

        // A cached thumbnail is only a fallback for a large request. Do not let it suppress a
        // first attempt to fetch the full-size avatar.
        Bitmap cached = CACHE.get(requestKey);
        if (cached != null) {
            Bitmap cachedBitmap = cached;
            DiagnosticLog.d(diagnosticContext, "AVATAR", () ->
                    "cache hit account=" + accountId +
                            ", endpoint=" + endpointLabel(requestKey) +
                            ", dimensions=" + dimensions(cachedBitmap));
            showIfCurrent(view, requestKey, cached, callback);
            return;
        }
        if (urls.length > 1 && isRecentlyMissing(requestKey)) {
            Bitmap fallbackCached = CACHE.get(urls[1]);
            if (fallbackCached != null) {
                Bitmap cachedBitmap = fallbackCached;
                DiagnosticLog.d(diagnosticContext, "AVATAR", () ->
                        "negative-cache fallback account=" + accountId +
                                ", endpoint=40, dimensions=" + dimensions(cachedBitmap));
                showIfCurrent(view, requestKey, fallbackCached, callback);
                return;
            }
        }

        EXECUTOR.execute(() -> {
            Bitmap bitmap = null;
            for (String url : urls) {
                Bitmap cachedCandidate = CACHE.get(url);
                if (cachedCandidate != null) {
                    bitmap = cachedCandidate;
                    Bitmap candidateBitmap = cachedCandidate;
                    String candidateEndpoint = endpointLabel(url);
                    DiagnosticLog.d(diagnosticContext, "AVATAR", () ->
                            "candidate cache hit account=" + accountId +
                                    ", endpoint=" + candidateEndpoint +
                                    ", dimensions=" + dimensions(candidateBitmap));
                    break;
                }
                if (isRecentlyMissing(url)) {
                    String skippedEndpoint = endpointLabel(url);
                    DiagnosticLog.d(diagnosticContext, "AVATAR", () ->
                            "candidate skipped by 404 cache account=" + accountId +
                                    ", endpoint=" + skippedEndpoint);
                    continue;
                }

                DownloadResult result = download(url);
                logDownloadResult(diagnosticContext, accountId, url, result);
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
        int status = -1;
        String type = null;
        long contentLength = -1L;
        try {
            connection = (HttpURLConnection) new URL(value).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "image/png,image/*");
            status = connection.getResponseCode();
            type = connection.getContentType();
            contentLength = connection.getContentLengthLong();
            if (isPermanentMissingStatus(status))
                return DownloadResult.notFound(status, type, contentLength);
            if (status != HttpURLConnection.HTTP_OK)
                return DownloadResult.failed(status, type, contentLength, null, "http");

            if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("image/"))
                return DownloadResult.failed(status, type, contentLength, null, "content-type");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream stream =
                         new DigestInputStream(connection.getInputStream(), digest)) {
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                byte[] drain = new byte[4096];
                while (stream.read(drain) != -1) {
                    // Finish consuming the response so the diagnostic digest covers the payload.
                }
                String payloadHash = shortHex(digest.digest());
                return bitmap == null
                        ? DownloadResult.failed(status, type, contentLength, payloadHash, "decode")
                        : DownloadResult.loaded(bitmap, status, type, contentLength, payloadHash);
            }
        } catch (Exception error) {
            return DownloadResult.failed(status, type, contentLength, null,
                    error.getClass().getSimpleName());
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    private static void logDownloadResult(Context context, String accountId, String url,
                                          DownloadResult result) {
        String endpoint = endpointLabel(url);
        String contentType = result.contentType == null ? "unknown" : result.contentType;
        String dimensions = result.bitmap == null ? "none" : dimensions(result.bitmap);
        String payloadHash = result.payloadHash == null ? "none" : result.payloadHash;
        DiagnosticLog.d(context, "AVATAR", () ->
                "download account=" + accountId +
                        ", endpoint=" + endpoint +
                        ", status=" + result.statusCode +
                        ", contentType=" + contentType +
                        ", contentLength=" + result.contentLength +
                        ", dimensions=" + dimensions +
                        ", payloadSha256=" + payloadHash +
                        ", outcome=" + result.outcome);
    }

    static String endpointLabel(String url) {
        if (url != null && url.startsWith(LARGE_BASE))
            return "default";
        if (url != null && url.startsWith(SMALL_BASE))
            return "40";
        return "unknown";
    }

    private static String dimensions(Bitmap bitmap) {
        return bitmap == null ? "none" : bitmap.getWidth() + "x" + bitmap.getHeight();
    }

    private static String shortHex(byte[] value) {
        if (value == null)
            return "none";
        StringBuilder out = new StringBuilder();
        int count = Math.min(value.length, 8);
        for (int i = 0; i < count; i++)
            out.append(String.format(Locale.ROOT, "%02x", value[i] & 0xff));
        return out.toString();
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
        final int statusCode;
        final String contentType;
        final long contentLength;
        final String payloadHash;
        final String outcome;

        private DownloadResult(Bitmap bitmap, boolean notFound, int statusCode, String contentType,
                               long contentLength, String payloadHash, String outcome) {
            this.bitmap = bitmap;
            this.notFound = notFound;
            this.statusCode = statusCode;
            this.contentType = contentType;
            this.contentLength = contentLength;
            this.payloadHash = payloadHash;
            this.outcome = outcome;
        }

        static DownloadResult loaded(Bitmap bitmap, int statusCode, String contentType,
                                     long contentLength, String payloadHash) {
            return new DownloadResult(bitmap, false, statusCode, contentType, contentLength,
                    payloadHash, "loaded");
        }

        static DownloadResult notFound(int statusCode, String contentType, long contentLength) {
            return new DownloadResult(null, true, statusCode, contentType, contentLength,
                    null, "404");
        }

        static DownloadResult failed(int statusCode, String contentType, long contentLength,
                                     String payloadHash, String outcome) {
            return new DownloadResult(null, false, statusCode, contentType, contentLength,
                    payloadHash, outcome == null ? "failed" : outcome);
        }
    }
}
