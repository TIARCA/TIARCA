package io.mrarm.irc.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.core.os.ConfigurationCompat;
import androidx.core.text.HtmlCompat;

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
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.mrarm.irc.R;
import io.mrarm.irc.config.QuickCommandSettings;

/** Executes the configurable local quick commands without blocking the UI thread. */
public final class QuickCommandHelper {

    public interface Callback {
        void onResult(String message);
        void onError(String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private QuickCommandHelper() { }

    public static void execute(Context context, QuickCommandSettings.Command command,
                               String argument, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                String result;
                switch (command) {
                    case WIKI: result = wiki(app, argument); break;
                    case CALC: result = calculate(app, argument); break;
                    case MOVIE: result = movie(app, argument); break;
                    case TIME: result = time(app, argument); break;
                    case DICTIONARY: result = dictionary(app, argument); break;
                    default: throw new IllegalArgumentException();
                }
                MAIN.post(() -> callback.onResult(result));
            } catch (UserException e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(app.getString(R.string.quick_command_failed)));
            }
        });
    }

    private static String wiki(Context context, String query) throws Exception {
        if (query.isEmpty()) throw new UserException(context.getString(R.string.quick_command_empty));
        String host = "it.wikipedia.org";
        String body = get("https://" + host + "/w/rest.php/v1/search/page?q=" +
                encode(query) + "&limit=1", null);
        JsonArray pages = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("pages");
        if (pages == null || pages.size() == 0)
            throw new UserException(context.getString(R.string.quick_wiki_not_found));
        JsonObject page = pages.get(0).getAsJsonObject();
        String title = string(page, "title");
        String excerpt = cleanHtml(string(page, "excerpt"));
        String description = cleanHtml(string(page, "description"));
        String summary = description != null && !description.isEmpty() ? description : excerpt;
        String link = "https://" + host + "/wiki/" + encodePath(title);
        return truncate(title + (summary == null || summary.isEmpty() ? "" : " — " + summary),
                330) + " — " + link;
    }

    private static String dictionary(Context context, String word) throws Exception {
        if (word.isEmpty()) throw new UserException(context.getString(R.string.quick_dictionary_empty));
        String title = word.trim();
        String definition = null;
        try {
            String address = "https://it.wiktionary.org/w/api.php?action=parse&format=json" +
                    "&formatversion=2&prop=text&redirects=1&page=" + encode(word);
            JsonObject root = JsonParser.parseString(get(address, null)).getAsJsonObject();
            if (root.has("parse")) {
                JsonObject parse = root.getAsJsonObject("parse");
                title = string(parse, "title");
                definition = WiktionaryDefinitionParser.fromHtml(string(parse, "text"));
            }
        } catch (Exception ignored) { }
        // The parse-tree endpoint is structured and preferred. Wikitext is kept as a
        // compatibility fallback because older MediaWiki installations may omit it.
        if (definition == null || definition.isEmpty()) {
            String address = "https://it.wiktionary.org/w/api.php?action=parse&format=json" +
                    "&prop=wikitext&redirects=1&page=" + encode(word);
            JsonObject root = JsonParser.parseString(get(address, null)).getAsJsonObject();
            if (!root.has("parse"))
                throw new UserException(context.getString(R.string.quick_dictionary_not_found));
            JsonObject parse = root.getAsJsonObject("parse");
            title = string(parse, "title");
            JsonObject wikitext = parse.getAsJsonObject("wikitext");
            if (wikitext != null && wikitext.has("*"))
                definition = WiktionaryDefinitionParser.fromWikitext(
                        wikitext.get("*").getAsString());
        }
        if (definition == null || definition.isEmpty())
            throw new UserException(context.getString(R.string.quick_dictionary_no_definition));
        return title + " — " + truncate(definition, 330) + " — https://it.wiktionary.org/wiki/" +
                encodePath(title);
    }

    private static String movie(Context context, String query) throws Exception {
        if (query.isEmpty()) throw new UserException(context.getString(R.string.quick_movie_empty));
        String key = QuickCommandSettings.getTmdbKey(context);
        if (key.isEmpty()) throw new UserException(context.getString(R.string.quick_movie_key_missing));
        String body = get("https://api.themoviedb.org/3/search/movie?api_key=" + encode(key) +
                "&language=it-IT&include_adult=false&query=" + encode(query), null);
        JsonArray results = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("results");
        if (results == null || results.size() == 0)
            throw new UserException(context.getString(R.string.quick_movie_not_found));
        JsonObject item = results.get(0).getAsJsonObject();
        String title = string(item, "title");
        String date = string(item, "release_date");
        String year = date != null && date.length() >= 4 ? date.substring(0, 4) : "s.d.";
        String overview = cleanText(string(item, "overview"));
        String vote = item.has("vote_average") ? new DecimalFormat("0.0")
                .format(item.get("vote_average").getAsDouble()) : "-";
        long id = item.get("id").getAsLong();
        String base = context.getString(R.string.quick_movie_result, title, year, vote);
        if (overview != null && !overview.isEmpty()) base += " — " + truncate(overview, 220);
        return base + " — https://www.themoviedb.org/movie/" + id + "?language=it-IT";
    }

    private static String time(Context context, String place) throws UserException {
        TimeZone zone = resolveZone(place);
        if (zone == null) throw new UserException(context.getString(R.string.quick_time_not_found));
        Locale locale = ConfigurationCompat.getLocales(
                context.getResources().getConfiguration()).get(0);
        if (locale == null)
            locale = Locale.getDefault();
        SimpleDateFormat format = new SimpleDateFormat("EEEE d MMMM yyyy, HH:mm:ss",
                locale);
        format.setTimeZone(zone);
        String label = place.isEmpty() ? zone.getDisplayName(false, TimeZone.SHORT, Locale.ITALIAN) : place;
        return context.getString(R.string.quick_time_result, label, format.format(new Date()),
                zone.getDisplayName(zone.inDaylightTime(new Date()), TimeZone.SHORT, locale));
    }

    private static TimeZone resolveZone(String place) {
        if (place == null || place.trim().isEmpty()) return TimeZone.getDefault();
        String key = place.trim().toLowerCase(Locale.ROOT);
        Map<String, String> common = new HashMap<>();
        common.put("roma", "Europe/Rome"); common.put("italia", "Europe/Rome");
        common.put("londra", "Europe/London"); common.put("parigi", "Europe/Paris");
        common.put("berlino", "Europe/Berlin"); common.put("madrid", "Europe/Madrid");
        common.put("new york", "America/New_York"); common.put("los angeles", "America/Los_Angeles");
        common.put("tokyo", "Asia/Tokyo"); common.put("pechino", "Asia/Shanghai");
        common.put("sydney", "Australia/Sydney"); common.put("mosca", "Europe/Moscow");
        common.put("utc", "UTC"); common.put("gmt", "GMT");
        String id = common.get(key);
        if (id != null) return TimeZone.getTimeZone(id);
        for (String candidate : TimeZone.getAvailableIDs()) {
            if (candidate.equalsIgnoreCase(place) || candidate.toLowerCase(Locale.ROOT)
                    .endsWith("/" + key.replace(' ', '_')))
                return TimeZone.getTimeZone(candidate);
        }
        return null;
    }

    private static String calculate(Context context, String expression) throws UserException {
        if (expression.isEmpty()) throw new UserException(context.getString(R.string.quick_calc_empty));
        if (expression.length() > 200) throw new UserException(context.getString(R.string.quick_calc_too_long));
        try {
            double result = new Calculator(expression).parse();
            if (Double.isNaN(result) || Double.isInfinite(result))
                throw new ArithmeticException();
            DecimalFormat format = new DecimalFormat("0.##########");
            return expression + " = " + format.format(result);
        } catch (Exception e) {
            throw new UserException(context.getString(R.string.quick_calc_invalid));
        }
    }

    private static String get(String address, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "TIARCA/0.5.5 (Android IRC client)");
        if (headers != null) for (Map.Entry<String, String> h : headers.entrySet())
            connection.setRequestProperty(h.getKey(), h.getValue());
        int status = connection.getResponseCode();
        if (status != 200) throw new IOException("HTTP " + status);
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), Charset.forName("UTF-8")))) {
            char[] buffer = new char[4096]; int read;
            while ((read = reader.read(buffer)) != -1 && body.length() < 1024 * 1024)
                body.append(buffer, 0, read);
        } finally { connection.disconnect(); }
        return body.toString();
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String encodePath(String value) throws Exception {
        return encode(value.replace(' ', '_')).replace("+", "%20");
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static String cleanHtml(String value) {
        if (value == null) return null;
        return cleanText(HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY).toString());
    }

    private static String cleanText(String value) {
        return value == null ? null : value.replace('\r', ' ').replace('\n', ' ')
                .replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1).trim() + "…";
    }

    private static final class UserException extends Exception {
        UserException(String message) { super(message); }
    }

    /** Recursive-descent calculator: +, -, *, /, %, ^, parentheses and decimal commas. */
    private static final class Calculator {
        private final String input; private int pos = -1, ch;
        Calculator(String input) { this.input = input.replace(',', '.'); }
        double parse() { next(); double x = expression(); spaces(); if (pos < input.length())
            throw new IllegalArgumentException(); return x; }
        void next() { ch = (++pos < input.length()) ? input.charAt(pos) : -1; }
        void spaces() { while (ch == ' ' || ch == '\t') next(); }
        boolean eat(int c) { spaces(); if (ch == c) { next(); return true; } return false; }
        double expression() { double x = term(); for (;;) { if (eat('+')) x += term();
            else if (eat('-')) x -= term(); else return x; } }
        double term() { double x = power(); for (;;) { if (eat('*')) x *= power();
            else if (eat('/')) x /= power(); else if (eat('%')) x %= power(); else return x; } }
        double power() { double x = factor(); if (eat('^')) x = Math.pow(x, power()); return x; }
        double factor() { if (eat('+')) return factor(); if (eat('-')) return -factor();
            double x; int start = pos; if (eat('(')) { x = expression(); if (!eat(')'))
                throw new IllegalArgumentException(); } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') next();
                x = Double.parseDouble(input.substring(start, pos));
            } else throw new IllegalArgumentException(); return x; }
    }
}
