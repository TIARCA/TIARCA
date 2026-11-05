package io.mrarm.irc.config;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import io.mrarm.irc.util.DefaultPreferences;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class StatusMessageDisplaySettingsTest {
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        SettingsHelper.getInstance(context);
        prefs = DefaultPreferences.get(context);
        prefs.edit().remove(ChatSettings.PREF_STATUS_MESSAGE_MODE)
                .remove(ChatSettings.PREF_SEPARATE_STATUS_MESSAGES).commit();
    }

    @Test
    public void newInstallDefaultsToPrefix() {
        assertEquals(StatusMessageDisplaySettings.MODE_PREFIX,
                StatusMessageDisplaySettings.getMode(prefs));
    }

    @Test
    public void migratesLegacyCheckedAndUncheckedValuesWithoutChangingSelection() {
        prefs.edit().putBoolean(ChatSettings.PREF_SEPARATE_STATUS_MESSAGES, true).commit();
        assertEquals(StatusMessageDisplaySettings.MODE_SEPARATE,
                StatusMessageDisplaySettings.getMode(prefs));
        StatusMessageDisplaySettings.migrateLegacyIfNeeded(prefs);
        assertEquals(StatusMessageDisplaySettings.MODE_SEPARATE,
                prefs.getString(ChatSettings.PREF_STATUS_MESSAGE_MODE, null));

        prefs.edit().remove(ChatSettings.PREF_STATUS_MESSAGE_MODE)
                .putBoolean(ChatSettings.PREF_SEPARATE_STATUS_MESSAGES, false).commit();
        StatusMessageDisplaySettings.migrateLegacyIfNeeded(prefs);
        assertEquals(StatusMessageDisplaySettings.MODE_PLAIN,
                StatusMessageDisplaySettings.getMode(prefs));
    }

    @Test
    public void explicitNewSelectionWinsOverLegacyAndSurvivesReset() {
        prefs.edit().putBoolean(ChatSettings.PREF_SEPARATE_STATUS_MESSAGES, true)
                .putString(ChatSettings.PREF_STATUS_MESSAGE_MODE,
                        StatusMessageDisplaySettings.MODE_PLAIN).commit();
        StatusMessageDisplaySettings.migrateLegacyIfNeeded(prefs);
        assertEquals(StatusMessageDisplaySettings.MODE_PLAIN,
                StatusMessageDisplaySettings.getMode(prefs));

        prefs.edit().remove(ChatSettings.PREF_STATUS_MESSAGE_MODE)
                .remove(ChatSettings.PREF_SEPARATE_STATUS_MESSAGES).commit();
        assertEquals(StatusMessageDisplaySettings.MODE_PREFIX,
                StatusMessageDisplaySettings.getMode(prefs));
    }
}
