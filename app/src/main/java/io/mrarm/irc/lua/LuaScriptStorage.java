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
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-script, private, flat text storage. Only the main app accesses files;
 * Lua sees event-scoped copied values through the isolated-service IPC.
 */
final class LuaScriptStorage {
    static final int MAX_KEYS = 16;
    static final int MAX_VALUE_BYTES = 8192;
    static final int MAX_TOTAL_BYTES = 128 * 1024;
    static final int MAX_FILE_BYTES = 160 * 1024;
    private static final String PREFIX = "lua_scripts/";
    private final File directory;

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

    synchronized LinkedHashMap<String, String> read(String scriptId) throws IOException {
        AtomicFile source = new AtomicFile(file(scriptId));
        if (!source.getBaseFile().exists()) return new LinkedHashMap<>();
        return decode(source.readFully());
    }

    private void write(String scriptId, byte[] data) throws IOException {
        AtomicFile target = new AtomicFile(file(scriptId));
        FileOutputStream stream = target.startWrite();
        try {
            stream.write(data);
            target.finishWrite(stream);
        } catch (IOException error) {
            target.failWrite(stream);
            throw error;
        }
    }

    synchronized void apply(String scriptId, String operation, String key, String value)
            throws IOException {
        if (!validKey(key)) throw new IOException("Invalid Lua storage key");
        LinkedHashMap<String, String> values = read(scriptId);
        if ("store".equals(operation)) {
            if (!validValue(value)) throw new IOException("Invalid Lua storage value");
            values.put(key, value);
        } else if ("erase".equals(operation)) {
            values.remove(key);
        } else {
            throw new IOException("Unsupported Lua storage operation");
        }
        write(scriptId, encode(values));
    }

    synchronized void delete(String scriptId) {
        if (validId(scriptId)) new AtomicFile(file(scriptId)).delete();
    }

    synchronized void export(ZipFile zip, ZipParameters params, String id) throws IOException {
        File state = file(id);
        if (!state.isFile()) return;
        byte[] bytes = encode(read(id)); // Do not export damaged or oversized state.
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
        if (encoded != null) write(id, encoded);
    }
}
