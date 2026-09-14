package io.mrarm.irc.util.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.UUID;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class UserPresetStoreTest {

    @Test
    public void importedPresetCanBeMarkedAndRemoved() {
        Context context = ApplicationProvider.getApplicationContext();
        ThemeInfo theme = new ThemeInfo();
        theme.uuid = UUID.randomUUID();

        UserPresetStore.unmark(context, theme);
        assertFalse(UserPresetStore.contains(context, theme));

        UserPresetStore.mark(context, theme);
        assertTrue(UserPresetStore.contains(context, theme));

        UserPresetStore.unmark(context, theme);
        assertFalse(UserPresetStore.contains(context, theme));
    }

    @Test
    public void fileNameFallbackDropsPortableAndLegacyExtensions() {
        assertTrue("My preset".equals(UserPresetStore.nameFromFile("My preset.ircpreset")));
        assertTrue("Preset".equals(UserPresetStore.nameFromFile("Preset.IRCPRESET")));
        assertTrue("Old colors".equals(UserPresetStore.nameFromFile("Old colors.irctheme")));
        assertTrue("Legacy".equals(UserPresetStore.nameFromFile("Legacy.IRCTHEME")));
        assertTrue(UserPresetStore.isLegacyThemeFile("Legacy.IRCTHEME"));
        assertFalse(UserPresetStore.isLegacyThemeFile("Preset.ircpreset"));
    }
}
