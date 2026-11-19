package io.mrarm.irc.lua;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Generic script-owned append-only JSONL storage. Only the parent process
 * accesses the filesystem; isolated Lua can request one validated line at a
 * time with its persistent-data consent. Never parses moderation decisions.
 */
final class LuaScriptJournal {
    private final Context context;
    LuaScriptJournal(Context context) { this.context = context.getApplicationContext(); }

    private File file(String script, String server) {
        if (!LuaScriptStorage.validId(script) || !LuaScriptStorage.validId(server))
            throw new IllegalArgumentException("Invalid journal scope");
        return new File(new File(new File(context.getFilesDir(), "lua_journals"), script),
                server + ".jsonl");
    }

    synchronized void append(String script, String server, String line) throws IOException {
        if (line == null || line.length() == 0 || line.indexOf('\n') >= 0 ||
                line.indexOf('\r') >= 0 ||
                line.getBytes(StandardCharsets.UTF_8).length > 8192)
            throw new IOException("Invalid journal line");
        try {
            new JSONObject(line); // Enforce a single JSON object per line.
        } catch (JSONException error) {
            throw new IOException("Journal line must be a JSON object", error);
        }
        File f = file(script, server);
        if (!f.getParentFile().isDirectory() && !f.getParentFile().mkdirs())
            throw new IOException("Cannot create journal directory");
        try (FileOutputStream out = new FileOutputStream(f, true)) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write('\n');
        }
    }

    synchronized long size(String script, String server) {
        return file(script, server).length();
    }

    synchronized File snapshot(String script, String server) throws IOException {
        File source = file(script, server);
        if (!source.isFile() || source.length() == 0)
            throw new IOException("No journal records");
        File cache = new File(context.getCacheDir(), "diagnostics");
        if (!cache.isDirectory() && !cache.mkdirs())
            throw new IOException("Cannot create export cache");
        File output = new File(cache, "tiarca-journal-" +
                UUID.fromString(script) + "-" + UUID.fromString(server) + ".jsonl");
        Files.copy(source.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return output;
    }

    /**
     * Start a fresh journal without destroying the old one.  The previous file
     * is renamed into a durable private archive before it is shared; cancelling
     * the Android chooser cannot delete the previous events.
     */
    synchronized File rotate(String script, String server) throws IOException {
        File current = file(script, server);
        if (!current.isFile() || current.length() == 0)
            throw new IOException("No journal records to rotate");
        File directory = new File(current.getParentFile(), "archives");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Cannot create journal archive directory");
        File archive = new File(directory, server + "-" +
                System.currentTimeMillis() + "-" + UUID.randomUUID() + ".jsonl");
        // Same filesystem and directory tree: rename before sharing.  No truncate
        // and no deletion; if copying to cache fails, the archive remains intact.
        Files.move(current.toPath(), archive.toPath(), StandardCopyOption.ATOMIC_MOVE);
        return copyForSharing(archive, "tiarca-archived-" + script + "-" + server +
                "-" + archive.getName().substring(server.length() + 1));
    }

    /** Reopen the latest durable archive after an interrupted/aborted share. */
    synchronized File snapshotLastArchive(String script, String server) throws IOException {
        File directory = new File(file(script, server).getParentFile(), "archives");
        File[] candidates = directory.listFiles((dir, name) ->
                name.startsWith(server + "-") && name.endsWith(".jsonl"));
        if (candidates == null || candidates.length == 0)
            throw new IOException("No previous journal archive");
        java.util.Arrays.sort(candidates, java.util.Comparator.comparing(File::getName));
        File last = candidates[candidates.length - 1];
        return copyForSharing(last, "tiarca-archived-" + script + "-" + last.getName());
    }

    private File copyForSharing(File source, String name) throws IOException {
        File cache = new File(context.getCacheDir(), "diagnostics");
        if (!cache.isDirectory() && !cache.mkdirs())
            throw new IOException("Cannot create export cache");
        File output = new File(cache, name);
        Files.copy(source.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return output;
    }

    void share(File snapshot) {
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", snapshot);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/x-ndjson");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, "Export Lua journal");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);
    }
}
