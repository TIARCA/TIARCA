package io.mrarm.irc.util;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keyless YouTube search using Piped first and trusted Invidious fallbacks. */
public final class YoutubeSearchHelper {

    public interface Callback {
        void onResult(String message);
        void onError();
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Pattern YOUTUBE_MODERN_RESULT = Pattern.compile(
            "\\\"videoWithContextRenderer\\\":\\{.*?\\\"headline\\\":\\{.*?" +
                    "\\\"text\\\":\\\"((?:\\\\.|[^\\\"\\\\])*)\\\".*?" +
                    "\\\"videoId\\\":\\\"([A-Za-z0-9_-]{11})\\\"",
            Pattern.DOTALL);
    private static final Pattern YOUTUBE_CLASSIC_RESULT = Pattern.compile(
            "\\\"videoRenderer\\\":\\{\\\"videoId\\\":\\\"([A-Za-z0-9_-]{11})\\\".*?" +
                    "\\\"title\\\":\\{\\\"runs\\\":\\[\\{\\\"text\\\":\\\"" +
                    "((?:\\\\.|[^\\\"\\\\])*)\\\"",
            Pattern.DOTALL);
    private static final String[] PIPED = {
            "https://pipedapi.kavin.rocks"
    };
    private static final String[] INVIDIOUS = {
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://yt.chocolatemoo53.com"
    };

    private YoutubeSearchHelper() { }

    public static void search(String query, Callback callback) {
        EXECUTOR.execute(() -> {
            String result = null;
            try { result = searchProviders(query); } catch (Exception ignored) { }
            String finalResult = result;
            MAIN.post(() -> {
                if (finalResult != null) callback.onResult(finalResult);
                else callback.onError();
            });
        });
    }

    private static String searchProviders(String query) throws IOException {
        String encoded = URLEncoder.encode(query, "UTF-8");
        try {
            String result = searchYoutubePage(encoded);
            if (result != null)
                return result;
        } catch (Exception ignored) { }
        for (String base : PIPED) {
            try {
                JsonObject response = JsonParser.parseString(get(base + "/search?q=" + encoded +
                        "&filter=videos")).getAsJsonObject();
                JsonArray items = response.getAsJsonArray("items");
                if (items == null) continue;
                for (JsonElement element : items) {
                    JsonObject item = element.getAsJsonObject();
                    if (!"stream".equals(itemString(item, "type"))) continue;
                    String id = videoId(itemString(item, "url"));
                    String title = cleanTitle(itemString(item, "title"));
                    if (id != null && title != null)
                        return title + " - https://youtu.be/" + id;
                }
            } catch (Exception ignored) { }
        }
        for (String base : INVIDIOUS) {
            try {
                JsonArray items = JsonParser.parseString(get(base + "/api/v1/search?q=" + encoded +
                        "&type=video")).getAsJsonArray();
                for (JsonElement element : items) {
                    JsonObject item = element.getAsJsonObject();
                    if (!"video".equals(itemString(item, "type"))) continue;
                    String id = videoId(itemString(item, "videoId"));
                    String title = cleanTitle(itemString(item, "title"));
                    if (id != null && title != null)
                        return title + " - https://youtu.be/" + id;
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    /**
     * YouTube currently exposes the first search results inside the public results page.
     * This avoids depending entirely on public Piped/Invidious instances, which frequently
     * reject mobile clients or become unavailable. No account or API key is used.
     */
    private static String searchYoutubePage(String encodedQuery) throws IOException {
        String page = getYoutubePage("https://www.youtube.com/results?search_query=" +
                encodedQuery);
        // Some variants embed the initial JSON in a JavaScript string using \xNN escapes.
        page = decodeHexEscapes(page);
        Matcher modern = YOUTUBE_MODERN_RESULT.matcher(page);
        if (modern.find())
            return formatResult(decodeJsonString(modern.group(1)), modern.group(2));
        Matcher classic = YOUTUBE_CLASSIC_RESULT.matcher(page);
        if (classic.find())
            return formatResult(decodeJsonString(classic.group(2)), classic.group(1));
        return null;
    }

    private static String formatResult(String title, String id) {
        title = cleanTitle(title);
        id = videoId(id);
        return title == null || id == null ? null : title + " - https://youtu.be/" + id;
    }

    private static String decodeJsonString(String value) {
        try {
            return JsonParser.parseString("\"" + value + "\"").getAsString();
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String decodeHexEscapes(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\\' && i + 3 < value.length() &&
                    value.charAt(i + 1) == 'x') {
                int high = Character.digit(value.charAt(i + 2), 16);
                int low = Character.digit(value.charAt(i + 3), 16);
                if (high >= 0 && low >= 0) {
                    decoded.append((char) ((high << 4) | low));
                    i += 3;
                    continue;
                }
            }
            decoded.append(value.charAt(i));
        }
        return decoded.toString();
    }

    private static String getYoutubePage(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) " +
                "AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36");
        int status = connection.getResponseCode();
        if (status != 200)
            throw new IOException("HTTP " + status);
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), Charset.forName("UTF-8")))) {
            char[] buffer = new char[16 * 1024];
            int read;
            while ((read = reader.read(buffer)) != -1 && body.length() < 2 * 1024 * 1024)
                body.append(buffer, 0, read);
        } finally {
            connection.disconnect();
        }
        return body.toString();
    }

    private static String get(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "RevolutionIRC/0.5.5");
        int status = connection.getResponseCode();
        if (status != 200) throw new IOException("HTTP " + status);
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), Charset.forName("UTF-8")))) {
            String line;
            while ((line = reader.readLine()) != null && body.length() < 1024 * 1024)
                body.append(line);
        } finally {
            connection.disconnect();
        }
        return body.toString();
    }

    private static String itemString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static String videoId(String value) {
        if (value == null) return null;
        int marker = value.indexOf("watch?v=");
        if (marker >= 0) value = value.substring(marker + 8);
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        int separator = value.indexOf('&');
        if (separator >= 0) value = value.substring(0, separator);
        return value.matches("[A-Za-z0-9_-]{11}") ? value : null;
    }

    private static String cleanTitle(String title) {
        if (title == null) return null;
        title = title.replace('\r', ' ').replace('\n', ' ').trim();
        if (title.isEmpty()) return null;
        return title.length() > 240 ? title.substring(0, 237) + "..." : title;
    }
}
