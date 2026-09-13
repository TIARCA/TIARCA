package io.mrarm.irc.util.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.config.ChatSettings;
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
    }

    @Test
    public void captureAndApplyPreserveInterfacePreferences() {
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

        assertEquals("it", theme.ui.language);
        assertEquals(AppearancePreset.COLOR_BLIND.getId(), theme.ui.appearancePreset);
        assertEquals("always", theme.ui.appBarCompactMode);
        assertEquals("monospace", theme.chat.font);
        assertEquals(Integer.valueOf(16), theme.chat.fontSize);
        assertEquals(Boolean.TRUE, theme.chat.globalFontEnabled);
        assertEquals(Boolean.FALSE, theme.chat.textAutocorrectEnabled);
        assertEquals(Boolean.TRUE, theme.chat.sendBoxAlwaysMultiline);

        preferences.edit().clear().commit();
        ThemeAppearance.apply(context, theme);

        assertEquals("it", preferences.getString(AppLocaleManager.PREF_APP_LANGUAGE, null));
        assertEquals(AppearancePreset.COLOR_BLIND.getId(), preferences.getString(
                AppearancePresetManager.PREF_APPEARANCE_PRESET, null));
        assertEquals("always", preferences.getString(ChatSettings.PREF_APPBAR_COMPACT_MODE, null));
        assertEquals("monospace", preferences.getString(ChatSettings.PREF_FONT, null));
        assertEquals(16, preferences.getInt(ChatSettings.PREF_FONT_SIZE, -1));
        assertTrue(preferences.getBoolean(ChatSettings.PREF_GLOBAL_FONT_ENABLED, false));
        assertFalse(preferences.getBoolean(ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED, true));
        assertTrue(preferences.getBoolean(ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE, false));
    }
}
