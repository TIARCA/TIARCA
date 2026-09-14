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

import io.mrarm.irc.config.ChatBackgroundSettings;
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
        assertEquals(ChatBackgroundSettings.TYPE_COLOR, theme.chat.backgroundType);
        assertNull(theme.chat.backgroundColor);
        assertEquals(ChatBackgroundSettings.SCALE_FILL, theme.chat.backgroundScale);
        assertEquals(Float.valueOf(ChatBackgroundSettings.DEFAULT_ZOOM),
                theme.chat.backgroundZoom);
        assertEquals(Float.valueOf(ChatBackgroundSettings.DEFAULT_FOCUS),
                theme.chat.backgroundFocusX);
        assertEquals(Float.valueOf(ChatBackgroundSettings.DEFAULT_FOCUS),
                theme.chat.backgroundFocusY);
        assertEquals(Integer.valueOf(ChatBackgroundSettings.DEFAULT_OPACITY),
                theme.chat.backgroundOpacity);
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

        assertEquals(Integer.valueOf(5), theme.formatVersion);
        assertEquals(AppearancePreset.GRAPHIC_LIGHT.getId(), theme.ui.appearancePreset);
        assertEquals("always", theme.ui.appBarCompactMode);
        assertEquals("monospace", theme.chat.font);
        assertEquals(Integer.valueOf(17), theme.chat.fontSize);
        assertEquals(Boolean.TRUE, theme.chat.globalFontEnabled);
        assertEquals(Boolean.TRUE, theme.chat.textAutocorrectEnabled);
        assertEquals(ChatBackgroundSettings.TYPE_COLOR, theme.chat.backgroundType);
        assertNull(theme.chat.backgroundColor);
        assertEquals(ChatBackgroundSettings.SCALE_FILL, theme.chat.backgroundScale);
        assertEquals(Float.valueOf(ChatBackgroundSettings.DEFAULT_ZOOM),
                theme.chat.backgroundZoom);
        assertEquals(Integer.valueOf(ChatBackgroundSettings.DEFAULT_OPACITY),
                theme.chat.backgroundOpacity);
        assertEquals("[HH:mm]", theme.messageLayout.timeFormat);
        assertEquals(Boolean.TRUE, theme.messageLayout.timeRight);
        assertNotNull(theme.messageLayout.normal);
    }

    @Test
    public void v3PresetGetsThemeColorBackgroundDefaults() {
        ThemeInfo theme = new ThemeInfo();
        theme.formatVersion = 3;
        theme.chat = new ThemeInfo.ChatSection();

        assertTrue(ThemePresetMigrator.migrate(context, theme));

        assertEquals(Integer.valueOf(5), theme.formatVersion);
        assertEquals(ChatBackgroundSettings.TYPE_COLOR, theme.chat.backgroundType);
        assertNull(theme.chat.backgroundColor);
        assertEquals(ChatBackgroundSettings.SCALE_FILL, theme.chat.backgroundScale);
        assertFalse(theme.assets.containsKey(ThemeInfo.ASSET_CHAT_BACKGROUND));
    }

    @Test
    public void v4BackgroundGetsDeterministicEditorDefaults() {
        ThemeInfo theme = new ThemeInfo();
        theme.formatVersion = 4;
        theme.chat = new ThemeInfo.ChatSection();
        theme.chat.backgroundType = ChatBackgroundSettings.TYPE_COLOR;
        theme.chat.backgroundScale = ChatBackgroundSettings.SCALE_FIT;

        assertTrue(ThemePresetMigrator.migrate(context, theme));

        assertEquals(Integer.valueOf(5), theme.formatVersion);
        assertEquals(ChatBackgroundSettings.SCALE_FIT, theme.chat.backgroundScale);
        assertEquals(Float.valueOf(1f), theme.chat.backgroundZoom);
        assertEquals(Float.valueOf(0.5f), theme.chat.backgroundFocusX);
        assertEquals(Float.valueOf(0.5f), theme.chat.backgroundFocusY);
        assertEquals(Integer.valueOf(100), theme.chat.backgroundOpacity);
    }

    @Test
    public void v5MatrixTransformIsPreserved() {
        ThemeInfo theme = new ThemeInfo();
        theme.formatVersion = 5;
        theme.chat = new ThemeInfo.ChatSection();
        theme.chat.backgroundType = ChatBackgroundSettings.TYPE_COLOR;
        theme.chat.backgroundScale = ChatBackgroundSettings.SCALE_MATRIX;
        theme.chat.backgroundZoom = 2.5f;
        theme.chat.backgroundFocusX = 0.25f;
        theme.chat.backgroundFocusY = 0.75f;
        theme.chat.backgroundOpacity = 45;

        ThemePresetMigrator.migrate(context, theme);

        assertEquals(ChatBackgroundSettings.SCALE_MATRIX, theme.chat.backgroundScale);
        assertEquals(Float.valueOf(2.5f), theme.chat.backgroundZoom);
        assertEquals(Float.valueOf(0.25f), theme.chat.backgroundFocusX);
        assertEquals(Float.valueOf(0.75f), theme.chat.backgroundFocusY);
        assertEquals(Integer.valueOf(45), theme.chat.backgroundOpacity);
    }

    @Test
    public void imageModeWithoutDeclaredAssetFallsBackToColor() {
        ThemeInfo theme = new ThemeInfo();
        theme.formatVersion = 4;
        theme.chat = new ThemeInfo.ChatSection();
        theme.chat.backgroundType = ChatBackgroundSettings.TYPE_IMAGE;
        theme.chat.backgroundScale = ChatBackgroundSettings.SCALE_FIT;

        assertTrue(ThemePresetMigrator.migrate(context, theme));

        assertEquals(ChatBackgroundSettings.TYPE_COLOR, theme.chat.backgroundType);
        assertEquals(ChatBackgroundSettings.SCALE_FIT, theme.chat.backgroundScale);
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
