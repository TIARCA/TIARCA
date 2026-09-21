package io.mrarm.irc.util.theme;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.config.ChatBackgroundSettings;
import io.mrarm.irc.config.ChatSettings;
import io.mrarm.irc.config.MessageFormatSettings;
import io.mrarm.irc.config.RightClockSettings;
import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.util.AppLocaleManager;
import io.mrarm.irc.util.DefaultPreferences;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ThemeAppearanceTest {

    private Context context;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SettingsHelper.getInstance(context);
        preferences = DefaultPreferences.get(context);
        preferences.edit().clear().commit();
        ChatBackgroundSettings.getImageFile(context).delete();
        ChatBackgroundSettings.discardCandidate(context);
    }

    @Test
    public void captureAndApplyPreservePresetPreferencesButNotLanguage() {
        preferences.edit()
                .putString(AppLocaleManager.PREF_APP_LANGUAGE, "it")
                .putString(AppearancePresetManager.PREF_APPEARANCE_PRESET,
                        AppearancePreset.COLOR_BLIND.getId())
                .putString(ChatSettings.PREF_FONT, "monospace")
                .putInt(ChatSettings.PREF_FONT_SIZE, 16)
                .putBoolean(ChatSettings.PREF_GLOBAL_FONT_ENABLED, true)
                .putBoolean(ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED, false)
                .putBoolean(ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE, true)
                .putString(ChatSettings.PREF_APPBAR_COMPACT_MODE, "always")
                .commit();

        ThemeInfo theme = new ThemeInfo();
        ThemeAppearance.capture(context, theme);

        assertEquals(AppearancePreset.COLOR_BLIND.getId(), theme.ui.appearancePreset);
        assertEquals("always", theme.ui.appBarCompactMode);
        assertEquals("monospace", theme.chat.font);
        assertEquals(Integer.valueOf(16), theme.chat.fontSize);
        assertEquals(Boolean.TRUE, theme.chat.globalFontEnabled);
        assertEquals(Boolean.FALSE, theme.chat.textAutocorrectEnabled);
        assertEquals(Boolean.TRUE, theme.chat.sendBoxAlwaysMultiline);
        assertEquals(ChatBackgroundSettings.TYPE_COLOR, theme.chat.backgroundType);
        assertNull(theme.chat.backgroundColor);
        assertEquals(ChatBackgroundSettings.SCALE_FILL, theme.chat.backgroundScale);
        assertEquals(Float.valueOf(ChatBackgroundSettings.DEFAULT_ZOOM),
                theme.chat.backgroundZoom);
        assertEquals(Float.valueOf(ChatBackgroundSettings.DEFAULT_FOCUS),
                theme.chat.backgroundFocusX);
        assertEquals(Integer.valueOf(ChatBackgroundSettings.DEFAULT_OPACITY),
                theme.chat.backgroundOpacity);

        preferences.edit().clear()
                .putString(AppLocaleManager.PREF_APP_LANGUAGE, "en")
                .commit();
        ThemeAppearance.apply(context, theme);

        assertEquals("en", preferences.getString(AppLocaleManager.PREF_APP_LANGUAGE, null));
        assertEquals(AppearancePreset.COLOR_BLIND.getId(), preferences.getString(
                AppearancePresetManager.PREF_APPEARANCE_PRESET, null));
        assertEquals("always", preferences.getString(ChatSettings.PREF_APPBAR_COMPACT_MODE, null));
        assertEquals("monospace", preferences.getString(ChatSettings.PREF_FONT, null));
        assertEquals(16, preferences.getInt(ChatSettings.PREF_FONT_SIZE, -1));
        assertTrue(preferences.getBoolean(ChatSettings.PREF_GLOBAL_FONT_ENABLED, false));
        assertFalse(preferences.getBoolean(ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED, true));
        assertTrue(preferences.getBoolean(ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE, false));
        assertEquals(ChatBackgroundSettings.TYPE_COLOR,
                ChatBackgroundSettings.getType(context));
    }

    @Test
    public void customBackgroundColorIsPortable() {
        ChatBackgroundSettings.useColor(context, 0xFF123456);
        ChatBackgroundSettings.setScale(context, ChatBackgroundSettings.SCALE_FIT);

        ThemeInfo theme = new ThemeInfo();
        ThemeAppearance.capture(context, theme);

        assertEquals(ChatBackgroundSettings.TYPE_COLOR, theme.chat.backgroundType);
        assertEquals(Integer.valueOf(0xFF123456), theme.chat.backgroundColor);
        assertEquals(ChatBackgroundSettings.SCALE_FIT, theme.chat.backgroundScale);

        ChatBackgroundSettings.useColor(context, 0xFFABCDEF);
        ChatBackgroundSettings.setScale(context, ChatBackgroundSettings.SCALE_STRETCH);
        ThemeAppearance.apply(context, theme);

        assertEquals(0xFF123456,
                ChatBackgroundSettings.getCustomColor(context, 0));
        assertEquals(ChatBackgroundSettings.SCALE_FIT,
                ChatBackgroundSettings.getScale(context));
    }

    @Test
    public void backgroundImageIsCopiedIntoPresetAndRestoredOnApply() throws Exception {
        byte[] image = "portable-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ChatBackgroundSettings.storeImage(context, new ByteArrayInputStream(image));
        ChatBackgroundSettings.setScale(context, ChatBackgroundSettings.SCALE_FIT);

        ThemeInfo theme = new ThemeInfo();
        theme.uuid = UUID.randomUUID();
        ThemeAppearance.capture(context, theme);

        String path = theme.assets.get(ThemeInfo.ASSET_CHAT_BACKGROUND);
        assertEquals("assets/background", path);
        assertEquals(ChatBackgroundSettings.TYPE_IMAGE, theme.chat.backgroundType);
        assertEquals(ChatBackgroundSettings.SCALE_FIT, theme.chat.backgroundScale);

        Map<String, InputStream> exported = ThemeAppearance.openAssetsForExport(context, theme);
        try {
            assertTrue(exported.containsKey(path));
            assertArrayEquals(image, readAll(exported.get(path)));
        } finally {
            for (InputStream input : exported.values())
                input.close();
        }

        ChatBackgroundSettings.getImageFile(context).delete();
        preferences.edit().putString(ChatBackgroundSettings.PREF_TYPE,
                ChatBackgroundSettings.TYPE_COLOR).commit();
        ThemeAppearance.apply(context, theme);

        assertEquals(ChatBackgroundSettings.TYPE_IMAGE, ChatBackgroundSettings.getType(context));
        assertArrayEquals(image, readFile(ChatBackgroundSettings.getImageFile(context)));
        assertEquals(ChatBackgroundSettings.SCALE_FIT,
                ChatBackgroundSettings.getScale(context));
    }

    @Test
    public void wysiwygBackgroundTransformIsPortable() throws Exception {
        byte[] image = "wysiwyg-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ChatBackgroundSettings.storeImage(context, new ByteArrayInputStream(image));
        ChatBackgroundSettings.setTransform(context, 2.25f, 0.2f, 0.8f, 47);

        ThemeInfo theme = new ThemeInfo();
        theme.uuid = UUID.randomUUID();
        ThemeAppearance.capture(context, theme);

        assertEquals(Integer.valueOf(5), theme.formatVersion);
        assertEquals(ChatBackgroundSettings.SCALE_MATRIX, theme.chat.backgroundScale);
        assertEquals(Float.valueOf(2.25f), theme.chat.backgroundZoom);
        assertEquals(Float.valueOf(0.2f), theme.chat.backgroundFocusX);
        assertEquals(Float.valueOf(0.8f), theme.chat.backgroundFocusY);
        assertEquals(Integer.valueOf(47), theme.chat.backgroundOpacity);

        preferences.edit()
                .putFloat(ChatBackgroundSettings.PREF_ZOOM, 5f)
                .putFloat(ChatBackgroundSettings.PREF_FOCUS_X, 1f)
                .putFloat(ChatBackgroundSettings.PREF_FOCUS_Y, 0f)
                .putInt(ChatBackgroundSettings.PREF_OPACITY, 100)
                .commit();
        ThemeAppearance.apply(context, theme);

        assertEquals(ChatBackgroundSettings.SCALE_MATRIX,
                ChatBackgroundSettings.getScale(context));
        assertEquals(2.25f, ChatBackgroundSettings.getZoom(context), 0.0001f);
        assertEquals(0.2f, ChatBackgroundSettings.getFocusX(context), 0.0001f);
        assertEquals(0.8f, ChatBackgroundSettings.getFocusY(context), 0.0001f);
        assertEquals(47, ChatBackgroundSettings.getOpacity(context));
    }

    @Test
    public void legacyPresetDoesNotInheritExistingDeviceAppearance() {
        preferences.edit()
                .putString(ChatSettings.PREF_FONT, "monospace")
                .putInt(ChatSettings.PREF_FONT_SIZE, 30)
                .putBoolean(ChatSettings.PREF_GLOBAL_FONT_ENABLED, true)
                .putBoolean(ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED, false)
                .putBoolean(ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE, true)
                .putString(ChatSettings.PREF_APPBAR_COMPACT_MODE, "always")
                .putBoolean(MessageFormatSettings.PREF_MESSAGE_AVATARS, true)
                .putBoolean(MessageFormatSettings.PREF_MESSAGE_CUSTOM_AVATARS, true)
                .putBoolean(RightClockSettings.PREF_MESSAGE_TIME_RIGHT, true)
                .putString(ChatBackgroundSettings.PREF_TYPE, ChatBackgroundSettings.TYPE_IMAGE)
                .putInt(ChatBackgroundSettings.PREF_COLOR, 0xFF00FF00)
                .putString(ChatBackgroundSettings.PREF_SCALE,
                        ChatBackgroundSettings.SCALE_MATRIX)
                .putFloat(ChatBackgroundSettings.PREF_ZOOM, 4f)
                .putFloat(ChatBackgroundSettings.PREF_FOCUS_X, 0.1f)
                .putFloat(ChatBackgroundSettings.PREF_FOCUS_Y, 0.9f)
                .putInt(ChatBackgroundSettings.PREF_OPACITY, 22)
                .commit();

        ThemeInfo legacy = new ThemeInfo();
        legacy.formatVersion = null;
        legacy.base = "default_dark";
        legacy.ui = null;
        legacy.chat = null;
        legacy.messageLayout = null;
        ThemeAppearance.apply(context, legacy);

        assertEquals(Integer.valueOf(ThemeArchive.FORMAT_VERSION), legacy.formatVersion);
        assertEquals("default", preferences.getString(ChatSettings.PREF_FONT, null));
        assertEquals(12, preferences.getInt(ChatSettings.PREF_FONT_SIZE, -1));
        assertFalse(preferences.getBoolean(ChatSettings.PREF_GLOBAL_FONT_ENABLED, true));
        assertTrue(preferences.getBoolean(ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED, false));
        assertFalse(preferences.getBoolean(ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE, true));
        assertEquals("auto", preferences.getString(ChatSettings.PREF_APPBAR_COMPACT_MODE, null));
        assertFalse(preferences.getBoolean(MessageFormatSettings.PREF_MESSAGE_AVATARS, true));
        assertFalse(preferences.getBoolean(MessageFormatSettings.PREF_MESSAGE_CUSTOM_AVATARS, true));
        assertFalse(preferences.getBoolean(RightClockSettings.PREF_MESSAGE_TIME_RIGHT, true));
        assertEquals(ChatBackgroundSettings.TYPE_COLOR,
                ChatBackgroundSettings.getType(context));
        assertFalse(ChatBackgroundSettings.hasCustomColor(context));
        assertEquals(ChatBackgroundSettings.SCALE_FILL,
                ChatBackgroundSettings.getScale(context));
        assertEquals(ChatBackgroundSettings.DEFAULT_ZOOM,
                ChatBackgroundSettings.getZoom(context), 0.0001f);
        assertEquals(ChatBackgroundSettings.DEFAULT_FOCUS,
                ChatBackgroundSettings.getFocusX(context), 0.0001f);
        assertEquals(ChatBackgroundSettings.DEFAULT_FOCUS,
                ChatBackgroundSettings.getFocusY(context), 0.0001f);
        assertEquals(ChatBackgroundSettings.DEFAULT_OPACITY,
                ChatBackgroundSettings.getOpacity(context));
    }

    @Test
    public void fixedWidthFieldIsLegacyCompatibilityOnly() {
        AppearancePresetManager manager = new AppearancePresetManager(context);
        try {
            ThemeInfo theme = new ThemeInfo();
            ThemeAppearance.capture(context, theme);

            assertEquals(Boolean.TRUE, theme.messageLayout.timeFixedWidth);

            theme.messageLayout.timeFixedWidth = false;
            ThemeAppearance.apply(context, theme);

            assertFalse(preferences.contains(MessageFormatSettings.LEGACY_MESSAGE_TIME_FIXED_WIDTH));
        } finally {
            manager.closeForTests();
        }
    }

    private static byte[] readFile(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            return readAll(input);
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1)
            output.write(buffer, 0, count);
        return output.toByteArray();
    }
}
