package io.mrarm.irc.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.config.ChatSettings;
import io.mrarm.irc.config.SettingsHelper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AppFontManagerTest {

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
    public void disabledGlobalFontLeavesTypefaceUntouched() {
        TextView view = new TextView(context);
        view.setTypeface(Typeface.SERIF, Typeface.BOLD);
        Typeface original = view.getTypeface();
        preferences.edit()
                .putString(ChatSettings.PREF_FONT, "monospace")
                .putBoolean(ChatSettings.PREF_GLOBAL_FONT_ENABLED, false)
                .commit();

        AppFontManager.applyToView(view);

        assertSame(original, view.getTypeface());
    }

    @Test
    public void enabledGlobalFontAppliesSelectedTypefaceAndKeepsStyle() {
        TextView view = new TextView(context);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        preferences.edit()
                .putString(ChatSettings.PREF_FONT, "monospace")
                .putBoolean(ChatSettings.PREF_GLOBAL_FONT_ENABLED, true)
                .commit();

        AppFontManager.applyToView(view);

        assertTrue(AppFontManager.isMonospace(view.getTypeface()));
        assertEquals(Typeface.BOLD, view.getTypeface().getStyle());
    }

    @Test
    public void technicalMonospaceTextIsPreservedForNonMonospaceGlobalFont() {
        TextView view = new TextView(context);
        view.setTypeface(Typeface.MONOSPACE);
        preferences.edit()
                .putString(ChatSettings.PREF_FONT, "serif")
                .putBoolean(ChatSettings.PREF_GLOBAL_FONT_ENABLED, true)
                .commit();

        AppFontManager.applyToView(view);

        assertTrue(AppFontManager.isMonospace(view.getTypeface()));
    }
}
