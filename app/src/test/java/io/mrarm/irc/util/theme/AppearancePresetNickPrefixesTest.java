package io.mrarm.irc.util.theme;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.config.MessageFormatSettings;
import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.util.DefaultPreferences;
import io.mrarm.irc.util.MessageBuilder;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AppearancePresetNickPrefixesTest {
    private Context context;
    private SharedPreferences preferences;
    private AppearancePresetManager manager;

    // The production builder is a process singleton. Robolectric may recreate its
    // application context between test methods, so never reuse an older instance.
    private void resetGlobalBuilder() {
        try {
            java.lang.reflect.Field field = MessageBuilder.class.getDeclaredField("sInstance");
            field.setAccessible(true);
            field.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Before public void setUp() {
        resetGlobalBuilder();
        context = ApplicationProvider.getApplicationContext();
        SettingsHelper.getInstance(context);
        preferences = DefaultPreferences.get(context);
        preferences.edit().clear().commit();
        manager = new AppearancePresetManager(context, false);
    }

    @After public void tearDown() {
        manager.closeForTests();
        resetGlobalBuilder();
    }

    @Test public void builtInPresetsResetOptionalPrefixFlag() {
        MessageBuilder builder = MessageBuilder.getInstance(context);
        for (AppearancePreset preset : AppearancePreset.values()) {
            if (!preset.isApplicable()) continue;
            builder.setShowNickPrefixes(true);
            builder.saveFormats();
            manager.applyPreset(preset);
            assertFalse("Preset " + preset, builder.getShowNickPrefixes());
            assertFalse(preferences.getBoolean(MessageFormatSettings.PREF_SHOW_NICK_PREFIXES, true));
        }
    }

    @Test public void changingManualOptionMarksPresetCustom() {
        manager.applyPreset(AppearancePreset.IRC_LIGHT);
        assertEquals(AppearancePreset.IRC_LIGHT, manager.getCurrentPreset());
        MessageBuilder builder = MessageBuilder.getInstance(context);
        builder.setShowNickPrefixes(true);
        builder.saveFormats();
        assertEquals(AppearancePreset.CUSTOM, manager.getCurrentPreset());
    }

    @Test public void exportedPresetKeepsOptInAndOldPresetDefaultsToOff() {
        MessageBuilder builder = MessageBuilder.getInstance(context);
        builder.setShowNickPrefixes(true);
        builder.saveFormats();
        ThemeInfo exported = new ThemeInfo();
        ThemeAppearance.capture(context, exported);
        assertEquals(Boolean.TRUE, exported.messageLayout.showNickPrefixes);

        builder.setShowNickPrefixes(false);
        builder.saveFormats();
        ThemeAppearance.apply(context, exported);
        assertTrue(builder.getShowNickPrefixes());
        assertTrue(preferences.getBoolean(MessageFormatSettings.PREF_SHOW_NICK_PREFIXES, false));

        exported.messageLayout.showNickPrefixes = null;
        ThemeAppearance.apply(context, exported);
        assertFalse(builder.getShowNickPrefixes());
        assertFalse(preferences.getBoolean(MessageFormatSettings.PREF_SHOW_NICK_PREFIXES, true));
    }
}
