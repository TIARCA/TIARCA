package io.mrarm.irc;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateAssetSelectionTest {
    @Test
    public void selectsOnlyTheStandardApkForTheAdvertisedVersion() {
        assertTrue(UpdateManager.isStandardApkAsset("TIARCA-v0.9.10.apk", "0.9.10"));
        assertFalse(UpdateManager.isStandardApkAsset("TIARCA-fdroid-v0.9.10.apk", "0.9.10"));
        assertFalse(UpdateManager.isStandardApkAsset("TIARCA-v0.9.10.apk.sha256", "0.9.10"));
        assertFalse(UpdateManager.isStandardApkAsset("TIARCA-v0.9.9.apk", "0.9.10"));
        assertFalse(UpdateManager.isStandardApkAsset(null, "0.9.10"));
        assertTrue(UpdateManager.isStandardApkAsset("TIARCA-v0.9.9.apk", "0.9.9"));
    }
}
