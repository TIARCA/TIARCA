package io.mrarm.irc.util.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import androidx.core.content.res.ResourcesCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.R;
import io.mrarm.irc.config.AppSettings;
import io.mrarm.irc.config.ChatSettings;
import io.mrarm.irc.config.MessageFormatSettings;
import io.mrarm.irc.config.QuickCommandSettings;
import io.mrarm.irc.config.RightClockSettings;
import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.util.DefaultPreferences;
import io.mrarm.irc.util.MessageBuilder;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AppearancePresetManagerTest {

    private Context context;
    private SharedPreferences preferences;
    private AppearancePresetManager manager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SettingsHelper.getInstance(context);
        preferences = DefaultPreferences.get(context);
        preferences.edit().clear().commit();
        manager = new AppearancePresetManager(context);
    }

    @After
    public void tearDown() {
        manager.closeForTests();
    }

    @Test
    public void ircLightAppliesHistoricalLightAppearance() {
        manager.applyPreset(AppearancePreset.IRC_LIGHT);

        assertEquals("default", preferences.getString(AppSettings.PREF_THEME, null));
        assertEquals("default", preferences.getString(ChatSettings.PREF_FONT, null));
        assertEquals(12, preferences.getInt(ChatSettings.PREF_FONT_SIZE, -1));
        assertFalse(preferences.getBoolean(MessageFormatSettings.PREF_MESSAGE_AVATARS, true));
        assertFalse(preferences.getBoolean(RightClockSettings.PREF_MESSAGE_TIME_RIGHT, true));
    }

    @Test
    public void ircDarkAppliesHistoricalDarkAppearance() {
        manager.applyPreset(AppearancePreset.IRC_DARK);

        assertEquals("default_dark", preferences.getString(AppSettings.PREF_THEME, null));
        assertEquals(AppearancePreset.IRC_DARK, manager.getCurrentPreset());
    }

    @Test
    public void graphicPresetsEnableAvatarsAndRightClock() {
        manager.applyPreset(AppearancePreset.GRAPHIC_LIGHT);
        assertEquals("default", preferences.getString(AppSettings.PREF_THEME, null));
        assertTrue(preferences.getBoolean(MessageFormatSettings.PREF_MESSAGE_AVATARS, false));
        assertTrue(preferences.getBoolean(RightClockSettings.PREF_MESSAGE_TIME_RIGHT, false));

        manager.applyPreset(AppearancePreset.GRAPHIC_DARK);
        assertEquals("default_dark", preferences.getString(AppSettings.PREF_THEME, null));
        assertTrue(preferences.getBoolean(MessageFormatSettings.PREF_MESSAGE_AVATARS, false));
        assertTrue(preferences.getBoolean(RightClockSettings.PREF_MESSAGE_TIME_RIGHT, false));
    }

    @Test
    public void terminalUsesSystemMonospaceAndVisiblePrefixes() {
        manager.applyPreset(AppearancePreset.TERMINAL);

        assertEquals("terminal_dark", preferences.getString(AppSettings.PREF_THEME, null));
        assertEquals("monospace", preferences.getString(ChatSettings.PREF_FONT, null));
        assertHasPrefixChip(MessageBuilder.getInstance(context).getMessageFormat());
        assertHasPrefixChip(MessageBuilder.getInstance(context).getActionMessageFormat());
        assertHasPrefixChip(MessageBuilder.getInstance(context).getNoticeMessageFormat());
    }

    @Test
    public void colorBlindUsesAtkinsonAt16SpAndRedundantMentionStyle() {
        manager.applyPreset(AppearancePreset.COLOR_BLIND);

        assertEquals("high_contrast_light",
                preferences.getString(AppSettings.PREF_THEME, null));
        assertEquals(AppearancePresetManager.FONT_ATKINSON_HYPERLEGIBLE_NEXT,
                preferences.getString(ChatSettings.PREF_FONT, null));
        assertEquals(16, preferences.getInt(ChatSettings.PREF_FONT_SIZE, -1));
        assertNotNull(ResourcesCompat.getFont(context, R.font.atkinson_hyperlegible_next));
        assertHasPrefixChip(MessageBuilder.getInstance(context).getMessageFormat());
        Spanned mention = (Spanned) MessageBuilder.getInstance(context)
                .getMentionMessageFormat();
        assertTrue(mention.getSpans(0, mention.length(), StyleSpan.class).length > 0);
        assertTrue(mention.getSpans(0, mention.length(), UnderlineSpan.class).length > 0);
    }

    @Test
    public void manualVisualChangeMarksPresetCustom() {
        manager.applyPreset(AppearancePreset.IRC_LIGHT);
        preferences.edit().putInt(ChatSettings.PREF_FONT_SIZE, 17).commit();

        assertEquals(AppearancePreset.CUSTOM, manager.getCurrentPreset());
    }

    @Test
    public void nonVisualChangeLeavesPresetSelected() {
        manager.applyPreset(AppearancePreset.IRC_DARK);
        preferences.edit().putBoolean(QuickCommandSettings.PREF_ENABLED, false).commit();

        assertEquals(AppearancePreset.IRC_DARK, manager.getCurrentPreset());
    }

    @Test
    public void portableInterfaceOptionsDoNotDirtyGraphicPreset() {
        manager.applyPreset(AppearancePreset.IRC_DARK);

        preferences.edit()
                .putBoolean(ChatSettings.PREF_GLOBAL_FONT_ENABLED, true)
                .putBoolean(ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED, false)
                .putBoolean(ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE, true)
                .commit();

        assertEquals(AppearancePreset.IRC_DARK, manager.getCurrentPreset());
    }

    @Test
    public void applyingPresetDoesNotDirtyItself() {
        manager.applyPreset(AppearancePreset.TERMINAL);

        assertFalse(manager.isApplyingPreset());
        assertEquals(AppearancePreset.TERMINAL, manager.getCurrentPreset());
    }

    @Test
    public void legacyFixedWidthPreferenceIsRemovedAndCannotPersist() {
        manager.closeForTests();
        preferences.edit()
                .putBoolean(MessageFormatSettings.PREF_MESSAGE_TIME_FIXED_WIDTH, false)
                .commit();

        manager = new AppearancePresetManager(context);
        assertFalse(preferences.contains(MessageFormatSettings.PREF_MESSAGE_TIME_FIXED_WIDTH));

        preferences.edit()
                .putBoolean(MessageFormatSettings.PREF_MESSAGE_TIME_FIXED_WIDTH, false)
                .commit();
        assertFalse(preferences.contains(MessageFormatSettings.PREF_MESSAGE_TIME_FIXED_WIDTH));

        manager.applyPreset(AppearancePreset.IRC_DARK);
        assertFalse(preferences.contains(MessageFormatSettings.PREF_MESSAGE_TIME_FIXED_WIDTH));
    }

    private static void assertHasPrefixChip(CharSequence format) {
        Spanned spanned = (Spanned) format;
        for (MessageBuilder.MetaChipSpan chip : spanned.getSpans(0, spanned.length(),
                MessageBuilder.MetaChipSpan.class)) {
            if (chip.getType() == MessageBuilder.MetaChipSpan.TYPE_SENDER_PREFIX)
                return;
        }
        throw new AssertionError("Sender-prefix chip missing");
    }
}
