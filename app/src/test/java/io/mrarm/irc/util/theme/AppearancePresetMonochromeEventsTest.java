package io.mrarm.irc.util.theme;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.config.EventDisplaySettings;
import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.util.DefaultPreferences;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AppearancePresetMonochromeEventsTest {

    private SharedPreferences preferences;
    private AppearancePresetManager manager;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        SettingsHelper.getInstance(context);
        preferences = DefaultPreferences.get(context);
        preferences.edit().clear().commit();
        manager = new AppearancePresetManager(context, false);
    }

    @After
    public void tearDown() {
        manager.closeForTests();
    }

    @Test
    public void builtInPresetsUseExpectedMonochromeEventDefaults() {
        assertMonochromeEvents(AppearancePreset.GRAPHIC_LIGHT, true);
        assertMonochromeEvents(AppearancePreset.GRAPHIC_DARK, true);
        assertMonochromeEvents(AppearancePreset.COLOR_BLIND, false);
        assertMonochromeEvents(AppearancePreset.TERMINAL, true);
    }

    private void assertMonochromeEvents(AppearancePreset preset, boolean expected) {
        manager.applyPreset(preset);
        String[] keys = {
                EventDisplaySettings.PREF_MONOCHROME_MODE,
                EventDisplaySettings.PREF_MONOCHROME_KICK,
                EventDisplaySettings.PREF_MONOCHROME_QUIT,
                EventDisplaySettings.PREF_MONOCHROME_JOIN_PART
        };
        for (String key : keys) {
            assertEquals(preset.getId() + ": " + key,
                    expected, preferences.getBoolean(key, !expected));
        }
    }
}
