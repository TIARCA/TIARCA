package io.mrarm.irc.util;

import android.content.Context;
import android.graphics.Color;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.mrarm.chatlib.dto.ChannelModeMessageInfo;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.irc.config.AutomatedSenderSettings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MessageBuilderMonochromeTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DefaultPreferences.get(context).edit()
                .putBoolean(AutomatedSenderSettings.PREF_MONOCHROME_BOTS, true)
                .commit();
    }

    @After
    public void tearDown() {
        DefaultPreferences.get(context).edit()
                .remove(AutomatedSenderSettings.PREF_MONOCHROME_BOTS)
                .commit();
    }

    @Test
    public void automatedModeEventStripsAllNestedForegroundColors() {
        List<ChannelModeMessageInfo.Entry> entries = new ArrayList<>();
        entries.add(new ChannelModeMessageInfo.Entry(
                ChannelModeMessageInfo.EntryType.NICK_FLAG, 'v', "TargetUser", false));

        MessageSenderInfo sender = new MessageSenderInfo(
                "BotAmIcIzIA", "services", "services.simosnap.com", null, null, true);
        MessageInfo message = new ChannelModeMessageInfo(sender, new Date(0), entries);

        CharSequence built = new MessageBuilder(context).buildMessage(message);
        assertTrue(built instanceof Spanned);
        Spanned rendered = (Spanned) built;
        String text = rendered.toString();

        int botIndex = text.indexOf("BotAmIcIzIA");
        int voiceIndex = text.toLowerCase().indexOf("voice");
        int targetIndex = text.indexOf("TargetUser");
        assertTrue(botIndex >= 0);
        assertTrue(voiceIndex >= 0);
        assertTrue(targetIndex >= 0);

        int background = StyledAttributesHelper.getColor(
                context, android.R.attr.colorBackground, Color.BLACK);
        int expected = ColorUtils.calculateLuminance(background) < 0.5
                ? Color.WHITE : Color.BLACK;

        assertEquals(expected, onlyForegroundColorAt(rendered, botIndex));
        assertEquals(expected, onlyForegroundColorAt(rendered, voiceIndex));
        assertEquals(expected, onlyForegroundColorAt(rendered, targetIndex));
    }

    private int onlyForegroundColorAt(Spanned text, int index) {
        ForegroundColorSpan[] spans = text.getSpans(
                index, index + 1, ForegroundColorSpan.class);
        assertEquals("Nested colour spans must be removed in monochrome bot rows", 1, spans.length);
        return spans[0].getForegroundColor();
    }
}
