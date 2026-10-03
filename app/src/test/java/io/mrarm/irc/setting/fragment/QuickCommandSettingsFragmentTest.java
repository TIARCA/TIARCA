package io.mrarm.irc.setting.fragment;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.List;

import io.mrarm.irc.config.QuickCommandSettings;
import io.mrarm.irc.setting.CheckBoxSetting;
import io.mrarm.irc.setting.EditTextSetting;
import io.mrarm.irc.setting.SecretEditTextSetting;
import io.mrarm.irc.setting.SettingsListAdapter;
import io.mrarm.irc.util.DefaultPreferences;
import io.mrarm.irc.util.EntryRecyclerViewAdapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class QuickCommandSettingsFragmentTest {

    @Test
    public void commandsHaveTwoLineUsageAndNoTriggerEditors() {
        SharedPreferences prefs = DefaultPreferences.get(RuntimeEnvironment.getApplication());
        prefs.edit().clear().commit();
        SettingsListAdapter adapter = createAdapter();
        List<EntryRecyclerViewAdapter.Entry> entries = ReflectionHelpers.getField(adapter, "mEntries");
        int commands = 0;
        int keys = 0;
        for (EntryRecyclerViewAdapter.Entry entry : entries) {
            if (entry instanceof SecretEditTextSetting) {
                keys++;
                continue;
            }
            assertFalse(entry instanceof EditTextSetting);
            if (!(entry instanceof CheckBoxSetting))
                continue;
            String description = ReflectionHelpers.<CharSequence>getField(entry, "mValue").toString();
            if (commands == 0) {
                // The first checkbox is the master switch.
                commands++;
                continue;
            }
            String[] lines = description.split("\n");
            assertEquals(2, lines.length);
            assertTrue(lines[0].contains(QuickCommandSettings.Command.values()[commands - 1].defaultTrigger));
            assertFalse(lines[1].isEmpty());
            commands++;
        }
        assertEquals(7, commands);
        assertEquals(1, keys);
    }

    @Test
    public void savedTriggerAndTmdbKeyAreNotResetWhenOpeningGuide() {
        SharedPreferences prefs = DefaultPreferences.get(RuntimeEnvironment.getApplication());
        prefs.edit().clear().putString(QuickCommandSettings.PREF_MOVIE_TRIGGER, "!film")
                .putString(QuickCommandSettings.PREF_TMDB_KEY, "user-test-key").commit();
        SettingsListAdapter adapter = createAdapter();
        List<EntryRecyclerViewAdapter.Entry> entries = ReflectionHelpers.getField(adapter, "mEntries");
        boolean found = false;
        for (EntryRecyclerViewAdapter.Entry entry : entries) {
            if (!(entry instanceof CheckBoxSetting))
                continue;
            CharSequence value = ReflectionHelpers.getField(entry, "mValue");
            if (value != null && value.toString().contains("!film"))
                found = true;
        }
        assertTrue(found);
        assertEquals("!film", QuickCommandSettings.getTrigger(
                RuntimeEnvironment.getApplication(), QuickCommandSettings.Command.MOVIE));
        assertEquals("user-test-key", prefs.getString(QuickCommandSettings.PREF_TMDB_KEY, ""));
    }

    private static SettingsListAdapter createAdapter() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat);
        QuickCommandSettingsFragment fragment = new QuickCommandSettingsFragment();
        activity.getSupportFragmentManager().beginTransaction().add(fragment, "quick-commands").commitNow();
        return fragment.createAdapter();
    }
}
