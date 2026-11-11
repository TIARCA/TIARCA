package io.mrarm.irc.lua;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source-level checks; IPC and process isolation still require on-device tests. */
public class LuaDiagnosticIntegrationTest {
    @Test public void isolatedAndNonExportedService() throws Exception {
        String manifest = new String(Files.readAllBytes(Path.of("src/main/AndroidManifest.xml")),
                StandardCharsets.UTF_8);
        assertTrue(manifest.contains("android:name=\".lua.LuaDiagnosticService\""));
        assertTrue(manifest.contains("android:isolatedProcess=\"true\""));
        assertTrue(manifest.contains("android:process=\":lua_sandbox\""));
        assertTrue(manifest.contains("android:exported=\"false\""));
    }

    @Test public void probeHasNoFilesystemOrIrcAccess() throws Exception {
        String code = new String(Files.readAllBytes(
                Path.of("src/main/java/io/mrarm/irc/lua/LuaDiagnosticService.java")),
                StandardCharsets.UTF_8);
        assertTrue(code.contains("Process.isIsolated()"));
        assertFalse(code.contains("getFilesDir("));
        assertFalse(code.contains("openFileInput("));
        assertFalse(code.contains("sendRaw"));
        assertTrue(code.contains("case \"loop\""));
        assertTrue(code.contains("case \"memory\""));
    }

    @Test public void sendConsentIsVisibleAndExplicitlyOptIn() throws Exception {
        String activity = Files.readString(
                Path.of("src/main/java/io/mrarm/irc/lua/LuaScriptsActivity.java"));
        // A message and a list in the same AlertDialog hid the send checkbox.
        // The dialog must contain both the explanatory text and a separate checkbox.
        assertTrue(activity.contains("new CheckBox(this)"));
        assertTrue(activity.contains("allowSend.setText(R.string.lua_scripts_send_permission)"));
        assertTrue(activity.contains("allowSend.setChecked(false)"));
        assertTrue(activity.contains("content.addView(allowSend)"));
        assertTrue(activity.contains(".setView(scroll)"));
        assertTrue(activity.contains("allowSend.isChecked()"));
        assertFalse(activity.contains(".setMultiChoiceItems("));
    }

    @Test public void nativeBuildUsesVendoredSource() throws Exception {
        String cmake = new String(Files.readAllBytes(Path.of("src/main/cpp/CMakeLists.txt")),
                StandardCharsets.UTF_8);
        assertTrue(cmake.contains("third_party/lua-5.5.1"));
        assertFalse(cmake.contains("file(DOWNLOAD"));
        assertFalse(cmake.contains("ExternalProject_Add"));
    }
}
