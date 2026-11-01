package io.mrarm.irc.util;

import android.content.Context;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;

import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.chatlib.dto.NickPrefixList;
import io.mrarm.irc.MessageFormatSettingsActivity;
import io.mrarm.irc.config.MessageFormatSettings;
import io.mrarm.irc.util.DefaultPreferences;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MessageBuilderNickPrefixTest {
    private Context context;
    private MessageBuilder builder;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DefaultPreferences.get(context).edit().clear().commit();
        builder = new MessageBuilder(context);
    }

    private MessageInfo msg(String prefix, MessageInfo.MessageType type) {
        return new MessageInfo(new MessageSenderInfo("Alice", "user", "host",
                prefix == null ? null : new NickPrefixList(prefix), null),
                new Date(1760000000000L), "Hello", type);
    }

    @Test public void optInIsOffByDefaultAndPersists() {
        assertFalse(builder.getShowNickPrefixes());
        assertFalse(builder.buildMessage(msg("@", MessageInfo.MessageType.NORMAL))
                .toString().contains("@Alice"));
        builder.setShowNickPrefixes(true);
        builder.saveFormats();
        assertTrue(new MessageBuilder(context).getShowNickPrefixes());
        assertTrue(DefaultPreferences.get(context).getBoolean(
                MessageFormatSettings.PREF_SHOW_NICK_PREFIXES, false));
    }

    @Test public void highestAvailablePrefixIsShownForMessagesActionsNoticesAndMentions() {
        builder.setShowNickPrefixes(true);
        for (MessageInfo.MessageType type : new MessageInfo.MessageType[] {
                MessageInfo.MessageType.NORMAL, MessageInfo.MessageType.ME,
                MessageInfo.MessageType.NOTICE }) {
            assertTrue(type.toString(), builder.buildMessage(msg("@+", type))
                    .toString().contains("@Alice"));
        }
        assertTrue(builder.buildMessageWithMention(
                msg("%", MessageInfo.MessageType.NORMAL)).toString().contains("%Alice"));
        assertTrue(builder.buildMessageWithMention(
                msg("+", MessageInfo.MessageType.ME)).toString().contains("+Alice"));
    }

    @Test public void absentOrEmptyPrefixDoesNotInventChannelStatus() {
        builder.setShowNickPrefixes(true);
        assertFalse(builder.buildMessage(msg(null, MessageInfo.MessageType.NORMAL))
                .toString().contains("@Alice"));
        assertFalse(builder.buildMessage(msg("", MessageInfo.MessageType.NORMAL))
                .toString().contains("@Alice"));
    }

    @Test public void explicitPrefixChipDoesNotProduceDuplicates() {
        builder.setMessageFormat(MessageFormatSettingsActivity.buildPresetMessageFormat(
                context, 0, false, true));
        builder.setShowNickPrefixes(true);
        String rendered = builder.buildMessage(msg("@", MessageInfo.MessageType.NORMAL)).toString();
        assertTrue(rendered.contains("@Alice"));
        assertFalse(rendered.contains("@@Alice"));
    }

    @Test public void eventsShowKnownPrefixButKeepUnknownUsersUnchanged() {
        builder.setShowNickPrefixes(true);
        assertTrue(builder.buildMessage(msg("@", MessageInfo.MessageType.JOIN))
                .toString().contains("@Alice"));
        assertTrue(builder.buildMessage(msg("+", MessageInfo.MessageType.PART))
                .toString().contains("+Alice"));
        assertTrue(builder.buildMessage(msg("%", MessageInfo.MessageType.QUIT))
                .toString().contains("%Alice"));
        assertFalse(builder.buildMessage(msg(null, MessageInfo.MessageType.QUIT))
                .toString().contains("@Alice"));
    }

    @Test public void swipeAndTapSpanRemainOnNicknameOnlyNotStatusPrefix() {
        builder.setShowNickPrefixes(true);
        ClickableSpan clickable = new ClickableSpan() {
            @Override public void onClick(View widget) {}
        };
        CharSequence text = builder.buildMessage(msg("@", MessageInfo.MessageType.NORMAL),
                clickable);
        Spanned span = (Spanned) text;
        int at = text.toString().indexOf("@Alice");
        assertTrue(at >= 0);
        assertEquals(at + 1, span.getSpanStart(clickable));
        assertEquals(at + 6, span.getSpanEnd(clickable));
    }
}
