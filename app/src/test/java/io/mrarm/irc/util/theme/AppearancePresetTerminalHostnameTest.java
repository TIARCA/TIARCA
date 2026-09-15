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

import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.util.DefaultPreferences;
import io.mrarm.irc.util.MessageBuilder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AppearancePresetTerminalHostnameTest {

    private Context context;
    private SharedPreferences preferences;
    private AppearancePresetManager manager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
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
    public void terminalEnablesEventHostnamesByDefault() {
        manager.applyPreset(AppearancePreset.TERMINAL);
        assertTrue(MessageBuilder.getInstance(context).getEventMessageShowHostname());
    }

    @Test
    public void leavingTerminalRestoresNonTerminalHostnameDefault() {
        manager.applyPreset(AppearancePreset.TERMINAL);
        assertTrue(MessageBuilder.getInstance(context).getEventMessageShowHostname());

        manager.applyPreset(AppearancePreset.IRC_DARK);
        assertFalse(MessageBuilder.getInstance(context).getEventMessageShowHostname());
    }
}
