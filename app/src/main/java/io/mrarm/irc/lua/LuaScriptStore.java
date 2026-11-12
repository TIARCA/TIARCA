package io.mrarm.irc.lua;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.AtomicFile;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.config.ServerConfigManager;

/**
 * Main-process ownership of user-imported Lua scripts. Actual execution always
 * happens elsewhere in the Android isolated service. Metadata and sources are
 * app-private and bounded; no downloaded plugins or executable binaries.
 */
public final class LuaScriptStore {
    public static final int MAX_SCRIPTS = 8;
    public static final int MAX_SOURCE_BYTES = 8192;

    private static final String BACKUP_INDEX = "lua_scripts/index.json";
    private static LuaScriptStore instance;

    public static synchronized LuaScriptStore get(Context context) {
        if (instance == null)
            instance = new LuaScriptStore(context.getApplicationContext());
        return instance;
    }

    public static final class Script {
        public final String id;
        public final String name;
        public final String sha256;
        public final String serverId;
        public final boolean enabled;
        public final boolean allowSend;
        public final boolean allowUserInfo;
        public final boolean allowModeration;
        public final boolean allowStorage;
        public final String error;

        Script(String id, String name, String hash, String serverId,
               boolean enabled, boolean allowSend, String error) {
            this(id, name, hash, serverId, enabled, allowSend, false, false, error);
        }

        Script(String id, String name, String hash, String serverId,
               boolean enabled, boolean allowSend, boolean allowUserInfo,
               boolean allowModeration, String error) {
            this(id, name, hash, serverId, enabled, allowSend, allowUserInfo,
                    allowModeration, false, error);
        }

        Script(String id, String name, String hash, String serverId,
               boolean enabled, boolean allowSend, boolean allowUserInfo,
               boolean allowModeration, boolean allowStorage, String error) {
            this.id = id;
            this.name = name;
            this.sha256 = hash;
            this.serverId = serverId;
            this.enabled = enabled;
            this.allowSend = allowSend;
            this.allowUserInfo = allowUserInfo;
            this.allowModeration = allowModeration;
            this.allowStorage = allowStorage;
            this.error = error;
        }

        Script configured(String serverId, boolean enabled, boolean allowSend, String error) {
            return configured(serverId, enabled, allowSend,
                    enabled && allowUserInfo, enabled && allowModeration,
                    enabled && allowStorage, error);
        }

        Script configured(String serverId, boolean enabled, boolean allowSend,
                          boolean allowUserInfo, boolean allowModeration, String error) {
            return configured(serverId, enabled, allowSend, allowUserInfo,
                    allowModeration, enabled && allowStorage, error);
        }

        Script configured(String serverId, boolean enabled, boolean allowSend,
                          boolean allowUserInfo, boolean allowModeration,
                          boolean allowStorage, String error) {
            return new Script(id, name, sha256, serverId, enabled, allowSend,
                    enabled && allowUserInfo, enabled && allowModeration,
                    enabled && allowStorage, error);
        }
    }

    private final Context context;
    private final File directory;
    private final File approvalMarker;
    private final AtomicFile index;
    private final LuaScriptStorage storage;
    private final ArrayList<Script> scripts = new ArrayList<>();
    private final Map<String, String> ephemeralLogs = new HashMap<>();
    private final Map<String, Output> ephemeralOutputs = new HashMap<>();

    /** Local UI output; it is never an IRC message and is not restored from backup. */
    public static final class Output {
        public final String title;
        public final String body;
        public final boolean list;
        Output(String title, String body, boolean list) {
            this.title = title;
            this.body = body;
            this.list = list;
        }
    }

    private LuaScriptStore(Context context) {
        this.context = context;
        directory = new File(context.getFilesDir(), "lua_scripts");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IllegalStateException("Cannot create private Lua script directory");
        index = new AtomicFile(new File(directory, "index.json"));
        storage = new LuaScriptStorage(directory);
        approvalMarker = new File(context.getNoBackupFilesDir(), "lua_approvals_v1");
        load();
        if (!approvalMarker.exists()) {
            // Android Auto Backup restores the script sources and index, but not
            // files in getNoBackupFilesDir(). Never inherit script permissions.
            ArrayList<Script> copies = new ArrayList<>();
            for (Script script : scripts)
                copies.add(script.configured("", false, false, ""));
            scripts.clear();
            scripts.addAll(copies);
            try {
                save();
                if (!approvalMarker.createNewFile() && !approvalMarker.isFile())
                    throw new IOException("Cannot initialize Lua consent marker");
            } catch (IOException error) {
                throw new IllegalStateException("Cannot initialize Lua approvals", error);
            }
        }
    }

    private void load() {
        try {
            if (!index.getBaseFile().isFile()) return;
            byte[] encoded = index.readFully();
            if (encoded.length > 16384) throw new IOException("Lua index too large");
            JSONArray list = new JSONArray(new String(encoded, StandardCharsets.UTF_8));
            if (list.length() > MAX_SCRIPTS) throw new IOException("Too many Lua scripts");
            for (int i = 0; i < list.length(); i++) {
                JSONObject entry = list.getJSONObject(i);
                Script s = fromJson(entry);
                for (Script existing : scripts)
                    if (existing.id.equals(s.id)) throw new IOException("Duplicate Lua script ID");
                scripts.add(s);
            }
        } catch (IOException | JSONException | IllegalArgumentException error) {
            // A malformed metadata file is never an excuse to enable a script.
            scripts.clear();
        }
    }

    // Script filenames also appear in local server-tab notices: never accept
    // separators, control characters or Unicode line separators from SAF/ZIP.
    static boolean validScriptName(String name) {
        if (name == null || name.isEmpty() || name.length() > 80 ||
                !name.toLowerCase(Locale.ROOT).endsWith(".lua") ||
                name.contains("/") || name.contains("\\")) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            int type = Character.getType(c);
            if (Character.isISOControl(c) ||
                    type == Character.LINE_SEPARATOR ||
                    type == Character.PARAGRAPH_SEPARATOR) return false;
        }
        return true;
    }

    private static Script fromJson(JSONObject obj) throws JSONException {
        String id = obj.getString("id");
        String hash = obj.getString("sha256");
        if (!validId(id) || !hash.matches("[a-f0-9]{64}"))
            throw new JSONException("Invalid Lua metadata");
        String name = obj.optString("name", "script.lua");
        if (!validScriptName(name))
            throw new JSONException("Invalid Lua filename");
        String server = obj.optString("server", "");
        if (!server.isEmpty() && !validId(server))
            throw new JSONException("Invalid Lua server UUID");
        return new Script(id, name, hash, server, obj.optBoolean("enabled", false),
                obj.optBoolean("send", false), obj.optBoolean("users", false),
                obj.optBoolean("moderation", false), obj.optBoolean("storage", false),
                obj.optString("error", ""));
    }

    private static JSONObject toJson(Script script) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("id", script.id);
        item.put("name", script.name);
        item.put("sha256", script.sha256);
        item.put("server", script.serverId);
        item.put("enabled", script.enabled);
        item.put("send", script.allowSend);
        item.put("users", script.allowUserInfo);
        item.put("moderation", script.allowModeration);
        item.put("storage", script.allowStorage);
        item.put("error", script.error);
        return item;
    }

    private void save() throws IOException {
        JSONArray array = new JSONArray();
        try {
            for (Script s : scripts) array.put(toJson(s));
        } catch (JSONException error) {
            throw new IOException("Cannot encode Lua metadata", error);
        }
        byte[] bytes = array.toString().getBytes(StandardCharsets.UTF_8);
        FileOutputStream stream = index.startWrite();
        try {
            stream.write(bytes);
            index.finishWrite(stream);
        } catch (IOException error) {
            index.failWrite(stream);
            throw error;
        }
    }

    private static boolean validId(String id) {
        if (id == null) return false;
        try {
            return UUID.fromString(id).toString().equals(id);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private File sourceFile(String id) {
        if (!validId(id)) throw new IllegalArgumentException("Invalid Lua script ID");
        return new File(directory, id + ".lua");
    }

    static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        if (input == null) throw new IOException("No script input");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (out.size() + count > maxBytes)
                throw new IOException("Lua script is too large");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    static String checkedSource(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_SOURCE_BYTES)
            throw new IOException("Lua script must be between 1 and 8192 bytes");
        String source;
        try {
            source = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) {
            throw new IOException("Lua script must be UTF-8", error);
        }
        if (source.indexOf('\0') >= 0) throw new IOException("Lua source contains NUL");
        return source;
    }

    private static String hash(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder str = new StringBuilder(64);
            for (byte b : digest) str.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return str.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public synchronized List<Script> list() {
        return new ArrayList<>(scripts);
    }

    public synchronized Script find(String id) {
        for (Script s : scripts) if (s.id.equals(id)) return s;
        return null;
    }

    public synchronized List<Script> enabledOn(String serverId) {
        ArrayList<Script> enabled = new ArrayList<>();
        for (Script s : scripts)
            if (s.enabled && s.serverId.equals(serverId)) enabled.add(s);
        return enabled;
    }

    public synchronized boolean anyEnabled() {
        for (Script s : scripts) if (s.enabled) return true;
        return false;
    }

    public synchronized byte[] sourceBytes(Script script) throws IOException {
        try (InputStream input = new java.io.FileInputStream(sourceFile(script.id))) {
            byte[] bytes = readBounded(input, MAX_SOURCE_BYTES);
            checkedSource(bytes);
            if (!hash(bytes).equals(script.sha256))
                throw new IOException("Lua source checksum mismatch");
            return bytes;
        }
    }

    private void writeSource(String id, byte[] data) throws IOException {
        File file = sourceFile(id);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
            output.getFD().sync();
        }
    }

    private Script importData(String displayName, byte[] data, String suppliedId)
            throws IOException {
        checkedSource(data);
        if (!validScriptName(displayName))
            throw new IOException("Invalid .lua filename");
        if (scripts.size() >= MAX_SCRIPTS) throw new IOException("Maximum 8 scripts");
        String digest = hash(data);
        for (Script old : scripts)
            if (old.sha256.equals(digest)) throw new IOException("Script already imported");
        String id = suppliedId == null ? UUID.randomUUID().toString() : suppliedId;
        if (!validId(id) || find(id) != null) throw new IOException("Duplicate/invalid script ID");
        Script script = new Script(id, displayName, digest, "", false, false, "");
        writeSource(id, data);
        scripts.add(script);
        try {
            save();
        } catch (IOException error) {
            scripts.remove(script);
            sourceFile(id).delete();
            throw error;
        }
        return script;
    }

    public Script importFromUri(Uri uri) throws IOException {
        String display = null;
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) display = cursor.getString(0);
        } catch (RuntimeException error) {
            throw new IOException("Cannot inspect selected document", error);
        }
        if (display == null) throw new IOException("Lua document has no filename");
        byte[] bytes;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            bytes = readBounded(input, MAX_SOURCE_BYTES);
        }
        // A remote SAF provider can stall. Never hold the shared script-store
        // monitor (also used by live IRC listeners) while reading that stream.
        synchronized (this) {
            return importData(display, bytes, null);
        }
    }

    public synchronized void enable(String id, String serverId, boolean send) throws IOException {
        enable(id, serverId, send, false, false);
    }

    public synchronized void enable(String id, String serverId, boolean send,
                                    boolean users, boolean moderation) throws IOException {
        enable(id, serverId, send, users, moderation, false);
    }

    public synchronized void enable(String id, String serverId, boolean send,
                                    boolean users, boolean moderation, boolean stored)
            throws IOException {
        if (!validId(serverId)) throw new IOException("Select one server for script consent");
        boolean exists = false;
        for (ServerConfigData server : ServerConfigManager.getInstance(context).getServers())
            if (serverId.equals(server.uuid.toString())) { exists = true; break; }
        if (!exists) throw new IOException("Selected server no longer exists");
        Script script = find(id);
        if (script == null) throw new IOException("Lua script not found");
        sourceBytes(script);
        replace(script, script.configured(serverId, true, send, users, moderation,
                stored, ""));
    }

    public synchronized void disable(String id) throws IOException {
        Script s = find(id);
        if (s != null) replace(s, s.configured(s.serverId, false, false, s.error));
    }

    public synchronized void fail(String id, String message) {
        Script s = find(id);
        if (s == null) return;
        String reason = message == null ? "Lua failure" : message;
        if (reason.length() > 240) reason = reason.substring(0, 240);
        try {
            replace(s, s.configured(s.serverId, false, false, reason));
        } catch (IOException ignored) {
            // An in-memory failure still disables execution in this process.
            int i = scripts.indexOf(s);
            if (i >= 0) scripts.set(i, s.configured(s.serverId, false, false, reason));
        }
    }

    private void replace(Script before, Script after) throws IOException {
        int i = scripts.indexOf(before);
        if (i < 0) throw new IOException("Lua script not found");
        scripts.set(i, after);
        try {
            save();
        } catch (IOException e) {
            scripts.set(i, before);
            throw e;
        }
    }

    /** Storage is read only in the main process and copied to each isolated dispatch. */
    synchronized ArrayList<String> storageSnapshot(String id) throws IOException {
        ArrayList<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : storage.read(id).entrySet()) {
            pairs.add(entry.getKey());
            pairs.add(entry.getValue());
        }
        return pairs;
    }

    synchronized void applyStorage(String id, String server, String operation,
                                   String key, String value) throws IOException {
        Script script = find(id);
        if (script == null || !script.enabled || !script.allowStorage ||
                !script.serverId.equals(server)) return;
        storage.apply(id, operation, key, value);
    }

    public synchronized void recordOutput(String id, String title, String body,
                                           boolean list) {
        if (find(id) == null || title == null || body == null ||
                title.isEmpty() || title.length() > 96 ||
                body.length() > 512 ||
                title.indexOf('\0') >= 0 || title.indexOf('\n') >= 0 ||
                title.indexOf('\r') >= 0 || body.indexOf('\0') >= 0)
            return;
        ephemeralOutputs.put(id, new Output(title, body, list));
    }

    public synchronized Output latestOutput(String id) {
        return ephemeralOutputs.get(id);
    }

    public synchronized void recordLog(String id, String log) {
        if (find(id) != null)
            ephemeralLogs.put(id, log == null ? "" : log.substring(0, Math.min(400, log.length())));
    }

    public synchronized String recentLog(String id) {
        return ephemeralLogs.get(id);
    }

    public synchronized void remove(String id) throws IOException {
        Script s = find(id);
        if (s == null) return;
        int position = scripts.indexOf(s);
        scripts.remove(position);
        try {
            save();
        } catch (IOException error) {
            // Do not show a script as removed when an I/O failure leaves its
            // previous (possibly enabled) approval intact on disk.
            scripts.add(position, s);
            throw error;
        }
        sourceFile(id).delete();
        ephemeralLogs.remove(id);
        ephemeralOutputs.remove(id);
        storage.delete(id);
    }

    public synchronized void clearAll() throws IOException {
        List<Script> previous = new ArrayList<>(scripts);
        scripts.clear();
        try {
            save();
        } catch (IOException error) {
            // An interrupted reset must not disagree with the persisted index
            // and silently re-enable old script permissions on next launch.
            scripts.addAll(previous);
            throw error;
        }
        ephemeralLogs.clear();
        ephemeralOutputs.clear();
        for (Script script : previous) storage.delete(script.id);
        File[] files = directory.listFiles();
        if (files != null)
            for (File f : files)
                if (f.isFile() && (f.getName().endsWith(".lua") ||
                        f.getName().endsWith(".state.json"))) f.delete();
    }

    public synchronized void exportToBackup(ZipFile zip, ZipParameters params)
            throws IOException {
        JSONArray manifest = new JSONArray();
        try {
            for (Script s : scripts) {
                // Never export approvals: backup restoration always requires new consent.
                JSONObject data = toJson(s.configured("", false, false, ""));
                manifest.put(data);
                params.setFileNameInZip("lua_scripts/" + s.id + ".lua");
                zip.addStream(new ByteArrayInputStream(sourceBytes(s)), params);
                storage.export(zip, params, s.id);
            }
            params.setFileNameInZip(BACKUP_INDEX);
            zip.addStream(new ByteArrayInputStream(
                    manifest.toString().getBytes(StandardCharsets.UTF_8)), params);
        } catch (JSONException error) {
            throw new IOException(error);
        }
    }

    public synchronized void restoreFromBackup(ZipFile zip) throws IOException {
        FileHeader header = zip.getFileHeader(BACKUP_INDEX);
        if (header == null) {
            clearAll();
            return;
        }
        List<Script> restored = new ArrayList<>();
        Map<String, byte[]> data = new HashMap<>();
        Map<String, byte[]> stored = new HashMap<>();
        try (InputStream input = zip.getInputStream(header)) {
            JSONArray manifest = new JSONArray(new String(readBounded(input, 16384),
                    StandardCharsets.UTF_8));
            if (manifest.length() > MAX_SCRIPTS) throw new IOException("Too many Lua scripts in backup");
            for (int i = 0; i < manifest.length(); i++) {
                Script entry = fromJson(manifest.getJSONObject(i));
                if (data.containsKey(entry.id)) throw new IOException("Duplicate Lua script in backup");
                FileHeader sourceHeader = zip.getFileHeader("lua_scripts/" + entry.id + ".lua");
                if (sourceHeader == null) throw new IOException("Lua source missing from backup");
                byte[] source;
                try (InputStream in = zip.getInputStream(sourceHeader)) {
                    source = readBounded(in, MAX_SOURCE_BYTES);
                }
                checkedSource(source);
                if (!hash(source).equals(entry.sha256))
                    throw new IOException("Lua backup integrity check failed");
                data.put(entry.id, source);
                stored.put(entry.id, storage.inspectBackup(zip, entry.id));
                restored.add(entry.configured("", false, false, ""));
            }
        } catch (JSONException error) {
            throw new IOException("Invalid Lua backup metadata", error);
        }
        clearAll();
        for (Script item : restored) {
            writeSource(item.id, data.get(item.id));
            storage.restoreValidated(item.id, stored.get(item.id));
            scripts.add(item);
        }
        save();
    }
}
