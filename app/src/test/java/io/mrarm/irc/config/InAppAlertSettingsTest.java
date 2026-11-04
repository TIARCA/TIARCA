package io.mrarm.irc.config;

import org.junit.Test;

import io.mrarm.chatlib.dto.MessageInfo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** In-app alert categories do not change Android system-notification rules. */
public class InAppAlertSettingsTest {

    private boolean alerts(boolean fromOtherUser, boolean direct, boolean mention,
                           boolean playback, MessageInfo.MessageType type,
                           boolean privateMessagesEnabled, boolean mentionsEnabled) {
        return InAppAlertSettings.shouldAlert(fromOtherUser, direct, mention, playback, type,
                privateMessagesEnabled, mentionsEnabled);
    }

    @Test
    public void bothCategoriesEnabledAllowPvtAndMentions() {
        assertTrue(alerts(true, true, false, false, MessageInfo.MessageType.NORMAL,
                true, true));
        assertTrue(alerts(true, true, false, false, MessageInfo.MessageType.ME,
                true, true));
        assertTrue(alerts(true, false, true, false, MessageInfo.MessageType.NORMAL,
                true, true));
        assertTrue(alerts(true, false, true, false, MessageInfo.MessageType.ME,
                true, true));
    }

    @Test
    public void privateOnlyIgnoresChannelMentions() {
        assertTrue(alerts(true, true, false, false, MessageInfo.MessageType.NORMAL,
                true, false));
        assertFalse(alerts(true, false, true, false, MessageInfo.MessageType.NORMAL,
                true, false));
    }

    @Test
    public void mentionsOnlyIgnoresPrivateMessages() {
        assertFalse(alerts(true, true, false, false, MessageInfo.MessageType.NORMAL,
                false, true));
        assertFalse(alerts(true, true, true, false, MessageInfo.MessageType.NORMAL,
                false, true));
        assertTrue(alerts(true, false, true, false, MessageInfo.MessageType.NORMAL,
                false, true));
    }

    @Test
    public void neitherCategoryTriggersAlerts() {
        assertFalse(alerts(true, true, false, false, MessageInfo.MessageType.NORMAL,
                false, false));
        assertFalse(alerts(true, false, true, false, MessageInfo.MessageType.ME,
                false, false));
    }

    @Test
    public void suppressOwnMessagesPlaybackAndOrdinaryChannelTraffic() {
        assertFalse(alerts(false, true, true, false, MessageInfo.MessageType.NORMAL,
                true, true));
        assertFalse(alerts(true, true, true, true, MessageInfo.MessageType.NORMAL,
                true, true));
        assertFalse(alerts(true, false, false, false, MessageInfo.MessageType.NORMAL,
                true, true));
    }

    @Test
    public void noticesAndSystemEventsDoNotTriggerMessageAlerts() {
        assertFalse(alerts(true, true, true, false, MessageInfo.MessageType.NOTICE,
                true, true));
    }
}
