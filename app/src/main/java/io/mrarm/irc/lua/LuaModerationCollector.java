package io.mrarm.irc.lua;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Opt-in append-only moderation journal. All file operations use the dispatcher IO thread. */
final class LuaModerationCollector {
    private static final Pattern AUTOMOD_DECISION = Pattern.compile(
            ".* ([0-9]{1,3}(?:\\.[0-9])?)% infrazione ([0-9]{1,6}) \\(([^)]+)\\)$");
    private final Context context;
    LuaModerationCollector(Context context) { this.context = context.getApplicationContext(); }
    boolean enabled(String server) {
        return context.getSharedPreferences("lua_moderation_collector", Context.MODE_PRIVATE)
                .getBoolean(server, false);
    }
    boolean setEnabled(String server, boolean active) {
        return context.getSharedPreferences("lua_moderation_collector", Context.MODE_PRIVATE)
                .edit().putBoolean(server, active).commit();
    }
    private File file(String server) {
        return new File(new File(context.getFilesDir(), "moderation-collector"),
                java.util.UUID.fromString(server).toString() + ".jsonl");
    }
    void append(String server, String event, String channel, String actor,
                String target, String detail, long timestamp, String previous,
                long previousTime, String origin, String ident, String host,
                String account) throws IOException {
        append(server, event, channel, actor, target, detail, timestamp, previous,
                previousTime, origin, ident, host, account, null, null);
    }

    void append(String server, String event, String channel, String actor,
                String target, String detail, long timestamp, String previous,
                long previousTime, String origin, String ident, String host,
                String account, String contextNick, String contextMatch) throws IOException {
        if (!enabled(server)) return;
        JSONObject obj = new JSONObject();
        try {
            obj.put("schema", 1);
            obj.put("server", server);
            obj.put("event", event);
            obj.put("channel", channel);
            obj.put("actor", actor);
            obj.put("target", target);
            obj.put("detail", detail);
            obj.put("timestamp", timestamp);
            obj.put("origin", origin);
            obj.put("ident", ident == null ? JSONObject.NULL : ident);
            obj.put("host", host == null ? JSONObject.NULL : host);
            obj.put("account", account == null ? JSONObject.NULL : account);
            if ("AUTOMOD".equals(event)) {
                Matcher match = AUTOMOD_DECISION.matcher(detail);
                if (match.matches()) {
                    obj.put("similarity_percent", Double.parseDouble(match.group(1)));
                    obj.put("infraction_level", Integer.parseInt(match.group(2)));
                    obj.put("requested_action", match.group(3));
                }
            }
            boolean autoMod = "AUTOMOD".equals(event);
            obj.put("prior_message", autoMod || previous == null ?
                    JSONObject.NULL : previous);
            obj.put("prior_timestamp", autoMod || previous == null ?
                    JSONObject.NULL : previousTime);
            obj.put("trigger_message", autoMod && previous != null ?
                    previous : JSONObject.NULL);
            obj.put("trigger_timestamp", autoMod && previous != null ?
                    previousTime : JSONObject.NULL);
            obj.put("prior_message_is_proven_cause", false);
            obj.put("context_nick", previous == null || "AUTOMOD".equals(event) ?
                    JSONObject.NULL : contextNick);
            obj.put("context_match", previous == null || "AUTOMOD".equals(event) ?
                    JSONObject.NULL : contextMatch);
        } catch (org.json.JSONException ex) { throw new IOException(ex); }
        File f = file(server);
        if (!f.getParentFile().isDirectory() && !f.getParentFile().mkdirs())
            throw new IOException("Cannot create collector directory");
        try (FileOutputStream out = new FileOutputStream(f, true)) {
            out.write((obj.toString()+"\n").getBytes(StandardCharsets.UTF_8));
        }
    }
    long size(String server) { return file(server).length(); }
    File snapshot(String server) throws IOException {
        File source = file(server);
        if (!source.isFile() || source.length() == 0) throw new IOException("No records");
        File dir = new File(context.getCacheDir(), "diagnostics");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create cache");
        File target = new File(dir, "tiarca-moderation-" +
                java.util.UUID.fromString(server) + ".jsonl");
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return target;
    }
    void share(File file) {
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName()+".fileprovider", file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/x-ndjson");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, "Export Collector");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);
    }
}
