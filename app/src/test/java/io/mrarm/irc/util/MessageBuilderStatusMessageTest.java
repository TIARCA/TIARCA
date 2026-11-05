package io.mrarm.irc.util;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Date;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.irc.config.ChatSettings;
import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.config.StatusMessageDisplaySettings;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MessageBuilderStatusMessageTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SettingsHelper.getInstance(context);
        DefaultPreferences.get(context).edit().remove(
                ChatSettings.PREF_SEPARATE_STATUS_MESSAGES).commit();
    }

    private MessageInfo makeMessage(MessageInfo.MessageType type, boolean status) {
        MessageInfo.Builder builder = new MessageInfo.Builder(
                new MessageSenderInfo("Alice", null, null, null, null), "hello ops", type);
        builder.setDate(new Date(0));
        builder.setStatusMessage(status);
        return builder.build();
    }

    @Test
    public void prefixOnlyInPrefixModeAndOnlyForStatusMessages() {
        MessageBuilder builder = new MessageBuilder(context);
        for (MessageInfo.MessageType type : new MessageInfo.MessageType[] {
                MessageInfo.MessageType.NORMAL, MessageInfo.MessageType.ME,
                MessageInfo.MessageType.NOTICE }) {
            MessageInfo ops = makeMessage(type, true);
            DefaultPreferences.get(context).edit().putString(
                    ChatSettings.PREF_STATUS_MESSAGE_MODE,
                    StatusMessageDisplaySettings.MODE_PREFIX).commit();
            assertTrue(builder.buildMessage(ops).toString().contains("(ops) hello ops"));
            assertFalse(builder.buildMessage(makeMessage(type, false)).toString()
                    .contains("(ops) hello ops"));
            DefaultPreferences.get(context).edit().putString(
                    ChatSettings.PREF_STATUS_MESSAGE_MODE,
                    StatusMessageDisplaySettings.MODE_PLAIN).commit();
            assertFalse(builder.buildMessage(ops).toString().contains("(ops) hello ops"));
            DefaultPreferences.get(context).edit().putString(
                    ChatSettings.PREF_STATUS_MESSAGE_MODE,
                    StatusMessageDisplaySettings.MODE_SEPARATE).commit();
            assertFalse(builder.buildMessage(ops).toString().contains("(ops) hello ops"));
            assertEquals("hello ops", ops.getMessage());
        }
    }
}
