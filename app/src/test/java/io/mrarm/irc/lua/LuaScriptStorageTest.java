package io.mrarm.irc.lua;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class LuaScriptStorageTest {
    private static final String ID = "1f1a37a1-b5be-44ef-a47d-2ba9980985cb";

    @Test public void keysAndQuotaAreBoundedAndSurviveNewInstance() throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        File dir = new File(app.getCacheDir(), "lua-persist-test");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        LuaScriptStorage first = new LuaScriptStorage(dir);
        first.delete(ID);
        try {
            assertFalse(LuaScriptStorage.validKey("../bad"));
            assertFalse(LuaScriptStorage.validKey("some/key"));
            assertFalse(LuaScriptStorage.validKey(""));
            assertFalse(LuaScriptStorage.validValue(""));
            assertFalse(LuaScriptStorage.validValue("x".repeat(385)));
            first.apply(ID, "store", "count", "1");
            first.apply(ID, "store", "other", "value");
            Map<String, String> restored = new LuaScriptStorage(dir).read(ID);
            assertEquals("1", restored.get("count"));
            assertEquals("value", restored.get("other"));
            first.apply(ID, "erase", "count", "1");
            assertFalse(new LuaScriptStorage(dir).read(ID).containsKey("count"));
            try {
                first.apply(ID, "store", "../escape", "x");
                fail("Path-like key accepted");
            } catch (IOException expected) { }
            try {
                first.apply(ID, "store", "too_long", "Z".repeat(400));
                fail("Oversized value accepted");
            } catch (IOException expected) { }
            assertEquals("value", new LuaScriptStorage(dir).read(ID).get("other"));
            for (int i = 0; i < 15; i++)
                first.apply(ID, "store", "key" + i, "v");
            try {
                first.apply(ID, "store", "key_overflow", "v");
                fail("Key count exceeded");
            } catch (IOException expected) { }
        } finally {
            first.delete(ID);
        }
        assertTrue(new LuaScriptStorage(dir).read(ID).isEmpty());
    }

    @Test public void backupRestoresStateButRevokesStorageAndAllOtherConsent()
            throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        LuaScriptStore store = LuaScriptStore.get(app);
        store.clearAll();
        String source = "tiarca.on('command', function(e) tiarca.log(e.text) end)";
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        byte[] sha = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hash = new StringBuilder();
        for (byte b : sha) hash.append(String.format(java.util.Locale.ROOT,
                "%02x", b & 0xff));
        JSONObject json = new JSONObject();
        json.put("id", ID);
        json.put("name", "persist.lua");
        json.put("sha256", hash.toString());
        json.put("server", "ef05b1d1-4fb3-4c13-ad82-2f0d3baaa898");
        json.put("enabled", true);
        json.put("send", true);
        json.put("users", true);
        json.put("moderation", true);
        json.put("storage", true);
        JSONArray index = new JSONArray().put(json);
        File file = File.createTempFile("lua-storage-", ".zip", app.getCacheDir());
        File roundTrip = File.createTempFile("lua-roundtrip-", ".zip", app.getCacheDir());
        File corrupt = File.createTempFile("lua-corrupt-", ".zip", app.getCacheDir());
        try {
            try (ZipFile zip = new ZipFile(file)) {
                ZipParameters p = new ZipParameters();
                p.setFileNameInZip("lua_scripts/" + ID + ".lua");
                zip.addStream(new ByteArrayInputStream(bytes), p);
                p.setFileNameInZip("lua_scripts/" + ID + ".state.json");
                zip.addStream(new ByteArrayInputStream(
                        "{\"counter\":\"17\"}".getBytes(StandardCharsets.UTF_8)), p);
                p.setFileNameInZip("lua_scripts/index.json");
                zip.addStream(new ByteArrayInputStream(
                        index.toString().getBytes(StandardCharsets.UTF_8)), p);
            }
            try (ZipFile zip = new ZipFile(file)) {
                store.restoreFromBackup(zip);
            }
            LuaScriptStore.Script script = store.find(ID);
            assertNotNull(script);
            assertFalse(script.enabled);
            assertFalse(script.allowStorage);
            assertFalse(script.allowSend);
            assertFalse(script.allowUserInfo);
            assertFalse(script.allowModeration);
            assertEquals("", script.serverId);
            assertEquals("17", store.storageSnapshot(ID).get(1));
            // A malformed state in an archive must not erase valid scripts
            // before its integrity has been checked.
            try (ZipFile zip = new ZipFile(corrupt)) {
                ZipParameters p = new ZipParameters();
                p.setFileNameInZip("lua_scripts/" + ID + ".lua");
                zip.addStream(new ByteArrayInputStream(bytes), p);
                p.setFileNameInZip("lua_scripts/" + ID + ".state.json");
                zip.addStream(new ByteArrayInputStream(
                        "{\"bad/key\":\"x\"}".getBytes(StandardCharsets.UTF_8)), p);
                p.setFileNameInZip("lua_scripts/index.json");
                zip.addStream(new ByteArrayInputStream(
                        index.toString().getBytes(StandardCharsets.UTF_8)), p);
            }
            try (ZipFile zip = new ZipFile(corrupt)) {
                store.restoreFromBackup(zip);
                fail("Invalid storage should reject entire restore");
            } catch (IOException expected) { }
            assertNotNull("Existing source must survive a bad backup", store.find(ID));
            assertEquals("17", store.storageSnapshot(ID).get(1));

            try (ZipFile zip = new ZipFile(roundTrip)) {
                store.exportToBackup(zip, new ZipParameters());
            }
            store.clearAll();
            try (ZipFile zip = new ZipFile(roundTrip)) {
                store.restoreFromBackup(zip);
            }
            assertEquals("17", store.storageSnapshot(ID).get(1));
            assertFalse(store.find(ID).allowStorage);
            store.remove(ID);
            assertTrue(store.storageSnapshot(ID).isEmpty());
        } finally {
            store.clearAll();
            file.delete();
            roundTrip.delete();
            corrupt.delete();
        }
    }

    @Test public void scriptOutputIsLocalAndBounded() throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        LuaScriptStore store = LuaScriptStore.get(app);
        store.clearAll();
        try {
            // Unknown scripts cannot create UI output.
            store.recordOutput(ID, "Title", "Alice\nBob", true);
            assertNull(store.latestOutput(ID));
        } finally {
            store.clearAll();
        }
    }
}
