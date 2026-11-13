package io.mrarm.irc.lua;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Run only AFTER an older signed debug APK with seeded Lua metadata has been
 * replaced via adb install -r while the app was open. This is an Android
 * package-manager integration check, not a substitute for live IRC testing.
 */
@RunWith(AndroidJUnit4.class)
public class LuaInstalledUpgradeInstrumentedTest {
    private static final String NETWORK = "2598f719-dfb6-4bad-a69c-ff78cecb9315";
    private static final String ACTIVE = "31f369c3-a99f-4ce1-bd2c-5b5d984b24ab";
    private static final String INACTIVE = "32f369c3-a99f-4ce1-bd2c-5b5d984b24ab";

    @Test public void inPlaceInstallPreservesLuaConsentAndPersistentCounter()
            throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File marker = new File(app.getNoBackupFilesDir(), "lua_approvals_v1");
        assertTrue("Same-device upgrade must keep the consent marker", marker.isFile());

        LuaScriptStore store = LuaScriptStore.get(app);
        LuaScriptStore.Script active = store.find(ACTIVE);
        LuaScriptStore.Script inactive = store.find(INACTIVE);
        assertNotNull("Active script source and metadata must survive APK update", active);
        assertNotNull("Disabled script must still be installed", inactive);
        assertTrue("Active script must remain enabled", active.enabled);
        assertTrue("Legacy single-network approval must migrate", active.isEnabledOn(NETWORK));
        assertEquals(1, active.serverIds.size());
        assertTrue("Storage consent must survive the same-device APK update",
                active.allowStorage);
        assertFalse("Inactive script must stay inactive", inactive.enabled);
        assertFalse(inactive.allowSend);
        assertFalse(inactive.allowStorage);
        assertEquals("42", store.storageSnapshot(ACTIVE).get(1));
        String content = new String(store.sourceBytes(active), StandardCharsets.UTF_8);
        assertTrue(content.contains("tiarca.on"));
    }
}
