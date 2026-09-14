package io.mrarm.irc.util.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.config.SettingsHelper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ThemePresetMigratorTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SettingsHelper.getInstance(context);
    }

    @Test
    public void legacyThemeGetsCompleteDeterministicCompatibilityDefaults() {
        ThemeInfo theme = new ThemeInfo();
        theme.formatVersion = null;
        theme.base = "default_dark";
        theme.ui = null;
        theme.chat = null;
        theme.messageLayout = null;

        assertTrue(ThemePresetMigrator.migrate(context, theme));

        assertEquals(Integer.valueOf(ThemeArchive.FORMAT_VERSION), theme.formatVersion);
        assertEquals(AppearancePreset.CUSTOM.getId(), theme.ui.appearancePreset);
        assertEquals("auto", theme.ui.appBarCompactMode);
        assertEquals("default", theme.chat.font);
        assertEquals(Integer.valueOf(12), theme.chat.fontSize);
        assertEquals(Boolean.FALSE, theme.chat.globalFontEnabled);
        assertEquals(Boolean.TRUE, theme.chat.textAutocorrectEnabled);
        assertEquals(Boolean.FALSE, theme.chat.sendBoxAlwaysMultiline);
        assertEquals(Boolean.FALSE, theme.chat.monochromeBots);
        assertEquals(Boolean.FALSE, theme.chat.messageAvatars);
        assertEquals(Boolean.FALSE, theme.chat.customAvatars);
        assertNotNull(theme.messageLayout.normal);
        assertNotNull(theme.messageLayout.mention);
        assertNotNull(theme.messageLayout.action);
        assertNotNull(theme.messageLayout.actionMention);
        assertNotNull(theme.messageLayout.notice);
        assertNotNull(theme.messageLayout.event);
        assertEquals(Boolean.FALSE, theme.messageLayout.eventHostname);
        assertEquals("[HH:mm.ss]", theme.messageLayout.timeFormat);
        assertEquals(Boolean.TRUE, theme.messageLayout.timeFixedWidth);
        assertEquals(Boolean.FALSE, theme.messageLayout.timeRight);
        assertTrue(theme.assets.isEmpty());

        assertFalse(ThemePresetMigrator.migrate(context, theme));
    }

    @Test
    public void v2ExplicitValuesArePreservedWhileMissingValuesAreFilled() {
        ThemeInfo theme = new ThemeInfo();
        theme.formatVersion = 2;
        theme.base = "default";
        theme.ui = new ThemeInfo.UiSection();
        theme.ui.appearancePreset = AppearancePreset.GRAPHIC_LIGHT.getId();
        theme.ui.appBarCompactMode = "always";
        theme.chat = new ThemeInfo.ChatSection();
        theme.chat.font = "monospace";
        theme.chat.fontSize = 17;
        theme.chat.globalFontEnabled = true;
        theme.messageLayout = new ThemeInfo.MessageLayoutSection();
        theme.messageLayout.timeFormat = "[HH:mm]";
        theme.messageLayout.timeRight = true;

        assertTrue(ThemePresetMigrator.migrate(context, theme));

        assertEquals(Integer.valueOf(3), theme.formatVersion);
        assertEquals(AppearancePreset.GRAPHIC_LIGHT.getId(), theme.ui.appearancePreset);
        assertEquals("always", theme.ui.appBarCompactMode);
        assertEquals("monospace", theme.chat.font);
        assertEquals(Integer.valueOf(17), theme.chat.fontSize);
        assertEquals(Boolean.TRUE, theme.chat.globalFontEnabled);
        assertEquals(Boolean.TRUE, theme.chat.textAutocorrectEnabled);
        assertEquals("[HH:mm]", theme.messageLayout.timeFormat);
        assertEquals(Boolean.TRUE, theme.messageLayout.timeRight);
        assertNotNull(theme.messageLayout.normal);
    }

    @Test
    public void v2FontAssetIsPromotedIntoGenericManifest() {
        ThemeInfo theme = new ThemeInfo();
        theme.formatVersion = 2;
        theme.chat = new ThemeInfo.ChatSection();
        theme.chat.fontAsset = "assets/font.ttf";

        ThemePresetMigrator.migrate(context, theme);

        assertEquals("assets/font.ttf", theme.chat.fontAsset);
        assertEquals("assets/font.ttf", theme.assets.get(ThemeInfo.ASSET_FONT));
    }

    @Test
    public void invalidLegacyFontAssetIsDropped() {
        ThemeInfo theme = new ThemeInfo();
        theme.formatVersion = 2;
        theme.chat = new ThemeInfo.ChatSection();
        theme.chat.fontAsset = "assets/../secret";

        ThemePresetMigrator.migrate(context, theme);

        assertNull(theme.chat.fontAsset);
        assertFalse(theme.assets.containsKey(ThemeInfo.ASSET_FONT));
    }

    @Test
    public void futurePresetIsNotRewrittenByCurrentMigrator() {
        ThemeInfo theme = new ThemeInfo();
        theme.formatVersion = ThemeArchive.FORMAT_VERSION + 1;
        theme.ui = null;
        theme.chat = null;
        theme.messageLayout = null;

        assertFalse(ThemePresetMigrator.migrate(context, theme));
        assertEquals(Integer.valueOf(ThemeArchive.FORMAT_VERSION + 1), theme.formatVersion);
        assertNull(theme.ui);
        assertNull(theme.chat);
        assertNull(theme.messageLayout);
    }
}
