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

    @Test public void nativeBuildUsesVendoredSource() throws Exception {
        String cmake = new String(Files.readAllBytes(Path.of("src/main/cpp/CMakeLists.txt")),
                StandardCharsets.UTF_8);
        assertTrue(cmake.contains("third_party/lua-5.5.1"));
        assertFalse(cmake.contains("file(DOWNLOAD"));
        assertFalse(cmake.contains("ExternalProject_Add"));
    }
}
