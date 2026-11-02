package io.mrarm.irc.config;

import org.junit.Test;

import io.mrarm.chatlib.dto.MessageInfo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InAppAlertSettingsTest {

    @Test
    public void incomingPrivateMessagesAndActionsAlert() {
        assertTrue(InAppAlertSettings.shouldAlert(true, true, false, false,
                MessageInfo.MessageType.NORMAL));
        assertTrue(InAppAlertSettings.shouldAlert(true, true, false, false,
                MessageInfo.MessageType.ME));
    }

    @Test
    public void incomingMentionsAlert() {
        assertTrue(InAppAlertSettings.shouldAlert(true, false, true, false,
                MessageInfo.MessageType.NORMAL));
        assertTrue(InAppAlertSettings.shouldAlert(true, false, true, false,
                MessageInfo.MessageType.ME));
    }

    @Test
    public void suppressOwnMessagesPlaybackAndOrdinaryChannelTraffic() {
        assertFalse(InAppAlertSettings.shouldAlert(false, true, true, false,
                MessageInfo.MessageType.NORMAL));
        assertFalse(InAppAlertSettings.shouldAlert(true, true, true, true,
                MessageInfo.MessageType.NORMAL));
        assertFalse(InAppAlertSettings.shouldAlert(true, false, false, false,
                MessageInfo.MessageType.NORMAL));
    }

    @Test
    public void noticesAndSystemEventsDoNotTriggerMessageAlerts() {
        assertFalse(InAppAlertSettings.shouldAlert(true, true, true, false,
                MessageInfo.MessageType.NOTICE));
    }
}
