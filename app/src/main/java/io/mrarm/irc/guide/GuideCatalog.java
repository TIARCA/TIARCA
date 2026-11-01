package io.mrarm.irc.guide;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.text.Normalizer;

/** Immutable, offline searchable guide entries (no network calls or script execution). */
public final class GuideCatalog {
    public static final String[] CATEGORIES = {"", "tiarca", "basic", "advanced", "network"};

    public static final class Entry {
        public final String id, category, title, summary, body, syntax, example, note, source;

        private Entry(JSONObject data) throws JSONException {
            id = data.getString("id");
            category = data.getString("category");
            title = data.getString("title");
            summary = data.getString("summary");
            body = data.getString("body");
            syntax = data.optString("syntax");
            example = data.optString("example");
            note = data.optString("note");
            source = data.optString("source");
            if (id.isEmpty() || title.isEmpty() || body.isEmpty()
                    || !isKnownCategory(category))
                throw new JSONException("Invalid guide entry");
        }
    }

    private GuideCatalog() { }

    private static boolean isKnownCategory(String category) {
        for (int i = 1; i < CATEGORIES.length; i++)
            if (CATEGORIES[i].equals(category))
                return true;
        return false;
    }

    public static List<Entry> parse(String json) throws JSONException {
        JSONArray array = new JSONObject(json).getJSONArray("entries");
        List<Entry> entries = new ArrayList<>();
        Set<String> identifiers = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            Entry entry = new Entry(array.getJSONObject(i));
            if (!identifiers.add(entry.id))
                throw new JSONException("Duplicate guide entry: " + entry.id);
            entries.add(entry);
        }
        if (entries.isEmpty())
            throw new JSONException("Empty guide");
        return Collections.unmodifiableList(entries);
    }

    private static String normalized(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    public static List<Entry> filter(List<Entry> entries, String category, String query) {
        List<Entry> results = new ArrayList<>();
        String needle = normalized(query).trim();
        for (Entry entry : entries) {
            if (category != null && !category.isEmpty() && !category.equals(entry.category))
                continue;
            String haystack = normalized(entry.title + " " + entry.summary + " " + entry.body
                    + " " + entry.syntax + " " + entry.example + " " + entry.note);
            if (needle.isEmpty() || haystack.contains(needle))
                results.add(entry);
        }
        return results;
    }
}
