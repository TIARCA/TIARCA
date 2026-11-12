package io.mrarm.irc.lua;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;

import static org.junit.Assert.*;

/**
 * Simulates replacement of an app process while leaving its private app data
 * intact, as a normal installed-APK update does. Not a physical install test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class LuaAppUpgradeTest {
    private static final String NETWORK = "2598f719-dfb6-4bad-a69c-ff78cecb9315";
    private static final String ON = "31f369c3-a99f-4ce1-bd2c-5b5d984b24ab";
    private static final String OFF = "32f369c3-a99f-4ce1-bd2c-5b5d984b24ab";

    private static JSONObject script(String id, byte[] source, boolean enabled)
            throws Exception {
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(source))
            hash.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        JSONObject item = new JSONObject();
        item.put("id", id);
        item.put("name", enabled ? "active.lua" : "inactive.lua");
        item.put("sha256", hash.toString());
        item.put("server", enabled ? NETWORK : "");
        item.put("enabled", enabled);
        item.put("send", false);
        item.put("users", false);
        item.put("moderation", false);
        item.put("storage", enabled);
        item.put("error", "");
        return item;
    }

    @Test public void apkReplacementPreservesEnablementAndPersistedValues()
            throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        ReflectionHelpers.setStaticField(LuaScriptStore.class, "instance", null);
        LuaScriptStore initial = LuaScriptStore.get(app);
        initial.clearAll();
        File directory = new File(app.getFilesDir(), "lua_scripts");
        File marker = new File(app.getNoBackupFilesDir(), "lua_approvals_v1");
        byte[] onSource = "tiarca.on('message', function(e) end)"
                .getBytes(StandardCharsets.UTF_8);
        byte[] offSource = "tiarca.on('command', function(e) end)"
                .getBytes(StandardCharsets.UTF_8);
        try {
            assertTrue("Normal upgrade retains consent marker", marker.isFile());
            Files.write(new File(directory, ON + ".lua").toPath(), onSource);
            Files.write(new File(directory, OFF + ".lua").toPath(), offSource);
            Files.write(new File(directory, ON + ".state.json").toPath(),
                    "{\"counter\":\"42\"}".getBytes(StandardCharsets.UTF_8));
            JSONArray array = new JSONArray();
            array.put(script(ON, onSource, true));
            array.put(script(OFF, offSource, false));
            Files.write(new File(directory, "index.json").toPath(),
                    array.toString().getBytes(StandardCharsets.UTF_8));

            // Same files, new store singleton: represents a fresh app process
            // after replacing the installed APK without clearing user data.
            ReflectionHelpers.setStaticField(LuaScriptStore.class, "instance", null);
            LuaScriptStore afterUpdate = LuaScriptStore.get(app);
            LuaScriptStore.Script active = afterUpdate.find(ON);
            LuaScriptStore.Script inactive = afterUpdate.find(OFF);
            assertNotNull(active);
            assertNotNull(inactive);
            assertTrue(active.enabled);
            assertEquals(NETWORK, active.serverId);
            assertFalse(inactive.enabled);
            assertFalse(inactive.allowStorage);
            assertArrayEquals(onSource, afterUpdate.sourceBytes(active));
            assertEquals("42", afterUpdate.storageSnapshot(ON).get(1));
            assertTrue(afterUpdate.enabledOn(NETWORK).stream()
                    .anyMatch(s -> ON.equals(s.id)));
            assertTrue(marker.isFile());
        } finally {
            LuaScriptStore.get(app).clearAll();
            ReflectionHelpers.setStaticField(LuaScriptStore.class, "instance", null);
        }
    }
}
