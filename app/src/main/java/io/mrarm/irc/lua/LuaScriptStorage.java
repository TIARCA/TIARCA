package io.mrarm.irc.lua;

import android.util.AtomicFile;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-script, private, flat text storage. Only the main app accesses files;
 * Lua sees event-scoped copied values through the isolated-service IPC.
 */
final class LuaScriptStorage {
    static final int MAX_KEYS = 32;
    static final int MAX_VALUE_BYTES = 8192;
    static final int MAX_TOTAL_BYTES = 128 * 1024;
    static final int MAX_FILE_BYTES = 160 * 1024;
    private static final String PREFIX = "lua_scripts/";
    private final File directory;
    private long nextVersion;
    private final LinkedHashMap<String, Snapshot> cached =
            new LinkedHashMap<String, Snapshot>(8, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Snapshot> entry) {
                    return size() > LuaScriptStore.MAX_SCRIPTS;
                }
            };

    /** Immutable committed state. Versions are local to this app process. */
    static final class Snapshot {
        final long version;
        private final LinkedHashMap<String, String> values;
        Snapshot(long version, Map<String, String> values) {
            this.version = version;
            this.values = new LinkedHashMap<>(values);
        }
        ArrayList<String> pairs() {
            ArrayList<String> pairs = new ArrayList<>();
            for (Map.Entry<String, String> e : values.entrySet()) {
                pairs.add(e.getKey()); pairs.add(e.getValue());
            }
            return pairs;
        }
    }

    LuaScriptStorage(File directory) {
        this.directory = directory;
    }

    static boolean validId(String id) {
        if (id == null) return false;
        try { return UUID.fromString(id).toString().equals(id); }
        catch (IllegalArgumentException ignored) { return false; }
    }

    static boolean validKey(String key) {
        return key != null && key.matches("[a-zA-Z0-9_.-]{1,48}");
    }

    static boolean validValue(String value) {
        if (value == null || value.isEmpty() || value.indexOf('\0') >= 0) return false;
        return value.getBytes(StandardCharsets.UTF_8).length <= MAX_VALUE_BYTES;
    }

    private File file(String scriptId) {
        if (!validId(scriptId)) throw new IllegalArgumentException("Invalid script ID");
        return new File(directory, scriptId + ".state.json");
    }

    private static LinkedHashMap<String, String> decode(byte[] data) throws IOException {
        if (data.length > MAX_FILE_BYTES) throw new IOException("Lua state is too large");
        final String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException error) {
            throw new IOException("Invalid Lua storage encoding", error);
        }
        try {
            JSONObject object = new JSONObject(json);
            if (object.length() > MAX_KEYS) throw new IOException("Too many Lua storage keys");
            LinkedHashMap<String, String> entries = new LinkedHashMap<>();
            ArrayList<String> keys = new ArrayList<>();
            java.util.Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            Collections.sort(keys);
            for (String key : keys) {
                if (!validKey(key) || !(object.opt(key) instanceof String))
                    throw new IOException("Invalid Lua storage entry");
                String value = object.getString(key);
                if (!validValue(value)) throw new IOException("Oversized Lua storage entry");
                entries.put(key, value);
            }
            checkTotal(entries);
            return entries;
        } catch (JSONException error) {
            throw new IOException("Malformed Lua storage", error);
        }
    }

    private static void checkTotal(Map<String, String> values) throws IOException {
        if (values.size() > MAX_KEYS) throw new IOException("Too many Lua storage keys");
        int total = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!validKey(entry.getKey()) || !validValue(entry.getValue()))
                throw new IOException("Invalid Lua storage entry");
            total += entry.getKey().getBytes(StandardCharsets.UTF_8).length +
                    entry.getValue().getBytes(StandardCharsets.UTF_8).length;
        }
        if (total > MAX_TOTAL_BYTES) throw new IOException("Lua storage quota exceeded");
    }

    private static byte[] encode(Map<String, String> values) throws IOException {
        checkTotal(values);
        JSONObject object = new JSONObject();
        try {
            for (Map.Entry<String, String> entry : values.entrySet())
                object.put(entry.getKey(), entry.getValue());
        } catch (JSONException error) {
            throw new IOException("Cannot encode Lua storage", error);
        }
        byte[] data = object.toString().getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_FILE_BYTES) throw new IOException("Lua state file too large");
        return data;
    }

    synchronized Snapshot snapshot(String scriptId) throws IOException {
        File base = file(scriptId);
        Snapshot known = cached.get(scriptId);
        if (known != null) return known;
        AtomicFile source = new AtomicFile(base);
        LinkedHashMap<String, String> values;
        try {
            values = decode(source.readFully());
        } catch (FileNotFoundException absent) {
            if (base.exists() || new File(base.getPath() + ".bak").exists()) throw absent;
            values = new LinkedHashMap<>();
        }
        Snapshot loaded = new Snapshot(++nextVersion, values);
        cached.put(scriptId, loaded);
        return loaded;
    }

    synchronized LinkedHashMap<String, String> read(String scriptId) throws IOException {
        return new LinkedHashMap<>(snapshot(scriptId).values);
    }

    private void write(String scriptId, byte[] data) throws IOException {
        AtomicFile target = new AtomicFile(file(scriptId));
        FileOutputStream stream = target.startWrite();
        try {
            stream.write(data);
            stream.getFD().sync();
            target.finishWrite(stream);
        } catch (IOException error) {
            target.failWrite(stream);
            throw error;
        }
        // AtomicFile.finishWrite may only log rename/sync failures. Verify the
        // committed bytes before allowing any externally visible action.
        if (!Arrays.equals(data, target.readFully()))
            throw new IOException("Lua state commit verification failed");
    }

    /** Apply every storage mutation in an event as a single transaction. */
    synchronized Snapshot applyBatch(String scriptId, long expectedVersion, List<String> actions)
            throws IOException {
        Snapshot before = snapshot(scriptId);
        if (expectedVersion != before.version)
            throw new IOException("Stale Lua storage revision");
        if (actions == null || actions.size() > 36 || actions.size() % 3 != 0)
            throw new IOException("Invalid Lua storage transaction");
        LinkedHashMap<String, String> values = new LinkedHashMap<>(before.values);
        for (int i = 0; i < actions.size(); i += 3) {
            String operation = actions.get(i), key = actions.get(i + 1), value = actions.get(i + 2);
            if (!"store".equals(operation) && !"erase".equals(operation)) continue;
            if (!validKey(key)) throw new IOException("Invalid Lua storage key");
            if ("store".equals(operation)) {
                if (!validValue(value)) throw new IOException("Invalid Lua storage value");
                values.put(key, value);
            } else {
                if (!"1".equals(value)) throw new IOException("Invalid Lua erase value");
                values.remove(key);
            }
            checkTotal(values);
        }
        if (values.equals(before.values)) return before;
        byte[] encoded = encode(values);
        try {
            write(scriptId, encoded);
        } catch (IOException failure) {
            cached.remove(scriptId);
            throw failure;
        }
        Snapshot committed = new Snapshot(++nextVersion, values);
        cached.put(scriptId, committed);
        return committed;
    }

    synchronized void apply(String scriptId, String operation, String key, String value)
            throws IOException {
        if (!"store".equals(operation) && !"erase".equals(operation))
            throw new IOException("Unsupported Lua storage operation");
        applyBatch(scriptId, snapshot(scriptId).version, Arrays.asList(operation, key, value));
    }

    synchronized void delete(String scriptId) {
        if (validId(scriptId)) {
            cached.remove(scriptId);
            new AtomicFile(file(scriptId)).delete();
        }
    }

    synchronized void invalidateAll() { cached.clear(); }

    synchronized void export(ZipFile zip, ZipParameters params, String id) throws IOException {
        Snapshot state = snapshot(id);
        if (state.values.isEmpty() && !file(id).isFile()) return;
        byte[] bytes = encode(state.values);
        params.setFileNameInZip(PREFIX + id + ".state.json");
        zip.addStream(new ByteArrayInputStream(bytes), params);
    }

    /** Prevalidate all state BEFORE the app clears existing scripts on restore. */
    synchronized byte[] inspectBackup(ZipFile zip, String id) throws IOException {
        FileHeader header = zip.getFileHeader(PREFIX + id + ".state.json");
        if (header == null) return null; // Compatible with pre-C6 backups.
        final byte[] data;
        try (InputStream in = zip.getInputStream(header)) {
            data = LuaScriptStore.readBounded(in, MAX_FILE_BYTES);
        }
        return encode(decode(data));
    }

    synchronized void restoreValidated(String id, byte[] encoded) throws IOException {
        cached.remove(id);
        if (encoded == null) { delete(id); return; }
        LinkedHashMap<String, String> restored = decode(encoded);
        write(id, encoded);
        cached.put(id, new Snapshot(++nextVersion, restored));
    }
}
