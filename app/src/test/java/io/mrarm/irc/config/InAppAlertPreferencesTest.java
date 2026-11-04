package io.mrarm.irc.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The two new preferences must be backward-compatible and persist independently. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class InAppAlertPreferencesTest {

    @Test
    public void legacyPreferencesEnableBothCategoriesAndSoundRemainsOff() {
        SharedPreferences prefs = SettingsHelper.getPreferences();
        prefs.edit().remove(InAppAlertSettings.PREF_PRIVATE_MESSAGES)
                .remove(InAppAlertSettings.PREF_MENTIONS)
                .remove(InAppAlertSettings.PREF_SOUND).commit();
        assertTrue(InAppAlertSettings.isPrivateMessagesEnabled());
        assertTrue(InAppAlertSettings.isMentionsEnabled());
        assertFalse(InAppAlertSettings.isSoundEnabled());
    }

    @Test
    public void independentSelectionsPersistInSharedPreferences() {
        SharedPreferences prefs = SettingsHelper.getPreferences();
        prefs.edit().putBoolean(InAppAlertSettings.PREF_PRIVATE_MESSAGES, false)
                .putBoolean(InAppAlertSettings.PREF_MENTIONS, true).commit();
        assertFalse(InAppAlertSettings.isPrivateMessagesEnabled());
        assertTrue(InAppAlertSettings.isMentionsEnabled());
        assertFalse(prefs.getBoolean(InAppAlertSettings.PREF_PRIVATE_MESSAGES, true));
        assertTrue(prefs.getBoolean(InAppAlertSettings.PREF_MENTIONS, false));
    }
}
