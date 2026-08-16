package io.mrarm.irc.config;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A small persistent index used to jump to mentions without rescanning chat history. */
public final class MentionStorage {

    private static final String PREFS_NAME = "mention_index";
    private static final int MAX_MENTIONS_PER_CONVERSATION = 50;
    private static final Type RECORD_LIST_TYPE = new TypeToken<List<Record>>() { }.getType();

    private static MentionStorage sInstance;

    public static synchronized MentionStorage getInstance(Context context) {
        if (sInstance == null)
            sInstance = new MentionStorage(context.getApplicationContext());
        return sInstance;
    }

    private final SharedPreferences preferences;

    private MentionStorage(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String key(UUID server, String channel) {
        return server.toString() + "\u001f" + channel;
    }

    private List<Record> read(UUID server, String channel) {
        String json = preferences.getString(key(server, channel), null);
        if (json == null)
            return new ArrayList<>();
        try {
            List<Record> records = SettingsHelper.getGson().fromJson(json, RECORD_LIST_TYPE);
            if (records == null)
                return new ArrayList<>();
            if (records.size() > MAX_MENTIONS_PER_CONVERSATION)
                return new ArrayList<>(records.subList(
                        records.size() - MAX_MENTIONS_PER_CONVERSATION, records.size()));
            return records;
        } catch (RuntimeException ignored) {
            return new ArrayList<>();
        }
    }

    private void write(UUID server, String channel, List<Record> records) {
        preferences.edit().putString(key(server, channel),
                SettingsHelper.getGson().toJson(records, RECORD_LIST_TYPE)).apply();
    }

    public synchronized boolean addMention(UUID server, String channel, String messageId) {
        return addMention(server, channel, messageId, false);
    }

    public synchronized boolean addMention(UUID server, String channel, String messageId,
                                           boolean reviewed) {
        if (channel == null || messageId == null)
            return false;
        List<Record> records = read(server, channel);
        for (Record record : records) {
            if (messageId.equals(record.messageId))
                return false;
        }
        records.add(new Record(messageId, reviewed));
        while (records.size() > MAX_MENTIONS_PER_CONVERSATION)
            records.remove(0);
        write(server, channel, records);
        return true;
    }

    public synchronized int getUnreviewedCount(UUID server, String channel) {
        int count = 0;
        for (Record record : read(server, channel)) {
            if (!record.reviewed)
                count++;
        }
        return count;
    }

    public synchronized List<String> getMessageIds(UUID server, String channel) {
        List<String> result = new ArrayList<>();
        for (Record record : read(server, channel)) {
            if (!record.reviewed)
                result.add(record.messageId);
        }
        return result;
    }

    public synchronized boolean markReviewed(UUID server, String channel, String messageId) {
        List<Record> records = read(server, channel);
        for (Record record : records) {
            if (messageId.equals(record.messageId) && !record.reviewed) {
                record.reviewed = true;
                write(server, channel, records);
                return true;
            }
        }
        return false;
    }

    public synchronized boolean markAllReviewed(UUID server, String channel) {
        List<Record> records = read(server, channel);
        boolean changed = false;
        for (Record record : records) {
            if (!record.reviewed) {
                record.reviewed = true;
                changed = true;
            }
        }
        if (changed)
            write(server, channel, records);
        return changed;
    }

    private static final class Record {
        String messageId;
        boolean reviewed;

        Record(String messageId, boolean reviewed) {
            this.messageId = messageId;
            this.reviewed = reviewed;
        }
    }
}
