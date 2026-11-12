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
import org.robolectric.util.ReflectionHelpers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.Assert.*;

/** Backup archives can preserve source code, but never active Lua permissions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class LuaScriptBackupTest {
    @Test public void restoresUserSourceButRevokesEveryPermission() throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        LuaScriptStore store = LuaScriptStore.get(app);
        store.clearAll();

        String id = "ca1d439a-bfde-4cc4-b8c7-5bcbd0eddf8a";
        byte[] source = "tiarca.on('message', function(e) tiarca.log(e.nick) end)"
                .getBytes(StandardCharsets.UTF_8);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(source);
        StringBuilder encoded = new StringBuilder();
        for (byte b : hash) encoded.append(String.format("%02x", b & 0xff));

        JSONObject script = new JSONObject();
        script.put("id", id);
        script.put("name", "backup-example.lua");
        script.put("sha256", encoded.toString());
        script.put("server", "ad9dfd3d-9d7d-4aeb-b047-a8d3cc673267");
        script.put("enabled", true);   // A malicious/legacy backup approval.
        script.put("send", true);
        script.put("error", "");
        JSONArray index = new JSONArray();
        index.put(script);

        File archive = File.createTempFile("lua-restore-", ".zip", app.getCacheDir());
        try {
            try (ZipFile zip = new ZipFile(archive)) {
                ZipParameters params = new ZipParameters();
                params.setFileNameInZip("lua_scripts/" + id + ".lua");
                zip.addStream(new ByteArrayInputStream(source), params);
                params.setFileNameInZip("lua_scripts/index.json");
                zip.addStream(new ByteArrayInputStream(
                        index.toString().getBytes(StandardCharsets.UTF_8)), params);
            }
            try (ZipFile zip = new ZipFile(archive)) {
                store.restoreFromBackup(zip);
            }
            assertEquals(1, store.list().size());
            LuaScriptStore.Script restored = store.list().get(0);
            assertEquals("backup-example.lua", restored.name);
            assertFalse(restored.enabled);
            assertFalse(restored.allowSend);
            assertEquals("", restored.serverId);
            assertArrayEquals(source, store.sourceBytes(restored));
        } finally {
            store.clearAll();
            archive.delete();
        }
    }

    @Test public void scriptNamesCannotInjectFakeLocalNotices() {
        assertTrue(LuaScriptStore.validScriptName("my-test.lua"));
        assertTrue(LuaScriptStore.validScriptName("Prova_ù.lua"));
        assertFalse(LuaScriptStore.validScriptName("../other.lua"));
        assertFalse(LuaScriptStore.validScriptName("bad\\name.lua"));
        assertFalse(LuaScriptStore.validScriptName("fake\nNOTICE.lua"));
        assertFalse(LuaScriptStore.validScriptName("fake\rNOTICE.lua"));
        assertFalse(LuaScriptStore.validScriptName("fake\u2028NOTICE.lua"));
        assertFalse(LuaScriptStore.validScriptName("fake\u2029NOTICE.lua"));
        assertFalse(LuaScriptStore.validScriptName("fake\u0000NOTICE.lua"));
    }

    @Test public void deviceTransferRevokesAllLuaConsentButKeepsStorage()
            throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        LuaScriptStore original = LuaScriptStore.get(app);
        original.clearAll();
        String id = "30f369c3-a99f-4ce1-bd2c-5b5d984b24ab";
        String serverId = "ad9dfd3d-9d7d-4aeb-b047-a8d3cc673267";
        byte[] source = "tiarca.on('command', function(e) end)"
                .getBytes(StandardCharsets.UTF_8);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(source);
        StringBuilder sha256 = new StringBuilder();
        for (byte b : digest)
            sha256.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        JSONObject config = new JSONObject();
        config.put("id", id);
        config.put("name", "transferred.lua");
        config.put("sha256", sha256.toString());
        config.put("server", serverId);
        config.put("enabled", true);
        config.put("send", true);
        config.put("users", true);
        config.put("moderation", true);
        config.put("storage", true);
        config.put("error", "");

        File directory = new File(app.getFilesDir(), "lua_scripts");
        File marker = new File(app.getNoBackupFilesDir(), "lua_approvals_v1");
        try {
            // Emulate Android restoring ordinary files but not the no-backup
            // consent marker on a new device.
            Files.write(new File(directory, id + ".lua").toPath(), source);
            Files.write(new File(directory, id + ".state.json").toPath(),
                    "{\"counter\":\"23\"}".getBytes(StandardCharsets.UTF_8));
            Files.write(new File(directory, "index.json").toPath(),
                    new JSONArray().put(config).toString()
                            .getBytes(StandardCharsets.UTF_8));
            assertTrue("Expected existing non-backed-up marker", marker.isFile());
            assertTrue("Cannot simulate a new device", marker.delete());
            ReflectionHelpers.setStaticField(LuaScriptStore.class, "instance", null);

            LuaScriptStore restored = LuaScriptStore.get(app);
            LuaScriptStore.Script script = restored.find(id);
            assertNotNull(script);
            assertFalse(script.enabled);
            assertFalse(script.allowSend);
            assertFalse(script.allowUserInfo);
            assertFalse(script.allowModeration);
            assertFalse(script.allowStorage);
            assertEquals("", script.serverId);
            assertEquals("23", restored.storageSnapshot(id).get(1));
            assertTrue(marker.isFile());
        } finally {
            LuaScriptStore.get(app).clearAll();
        }
    }

    @Test public void restoresOldBackupWithoutScriptsAsEmptyState() throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        LuaScriptStore store = LuaScriptStore.get(app);
        File archive = File.createTempFile("lua-legacy-", ".zip", app.getCacheDir());
        try {
            // A valid ZIP containing an unrelated settings entry.
            try (ZipFile zip = new ZipFile(archive)) {
                ZipParameters p = new ZipParameters();
                p.setFileNameInZip("preferences.json");
                zip.addStream(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)), p);
            }
            try (ZipFile zip = new ZipFile(archive)) {
                store.restoreFromBackup(zip);
            }
            assertTrue(store.list().isEmpty());
        } finally {
            store.clearAll();
            archive.delete();
        }
    }
}
