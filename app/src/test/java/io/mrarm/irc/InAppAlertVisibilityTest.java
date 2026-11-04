package io.mrarm.irc;

import org.junit.Test;

import java.util.UUID;

import io.mrarm.chatlib.irc.IRCCaseMapping;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InAppAlertVisibilityTest {

    private static final UUID SERVER_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SERVER_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    public void suppressesMentionsInTheDisplayedChannel() {
        assertTrue(InAppAlertVisibility.isConversationVisible(
                SERVER_A, "#amicizia", SERVER_A, "#AMICIZIA", IRCCaseMapping.RFC1459));
    }

    @Test
    public void suppressesPrivateMessagesInTheDisplayedQuery() {
        assertTrue(InAppAlertVisibility.isConversationVisible(
                SERVER_A, "Alice", SERVER_A, "aLiCe", IRCCaseMapping.RFC1459));
    }

    @Test
    public void keepsAlertsFromDifferentConversationsAndServers() {
        assertFalse(InAppAlertVisibility.isConversationVisible(
                SERVER_A, "#amicizia", SERVER_A, "#altra", IRCCaseMapping.RFC1459));
        assertFalse(InAppAlertVisibility.isConversationVisible(
                SERVER_A, "Alice", SERVER_A, "Bob", IRCCaseMapping.RFC1459));
        assertFalse(InAppAlertVisibility.isConversationVisible(
                SERVER_A, "#amicizia", SERVER_B, "#amicizia", IRCCaseMapping.RFC1459));
    }

    @Test
    public void doesNotSuppressWhenServerStatusOrNoChatIsDisplayed() {
        assertFalse(InAppAlertVisibility.isConversationVisible(
                SERVER_A, null, SERVER_A, "#amicizia", IRCCaseMapping.RFC1459));
        assertFalse(InAppAlertVisibility.isConversationVisible(
                null, "#amicizia", SERVER_A, "#amicizia", IRCCaseMapping.RFC1459));
        assertFalse(InAppAlertVisibility.isConversationVisible(
                SERVER_A, "#amicizia", SERVER_A, null, IRCCaseMapping.RFC1459));
    }

    @Test
    public void respectsTheServersIrcCaseMapping() {
        assertTrue(InAppAlertVisibility.isConversationVisible(
                SERVER_A, "#[test]", SERVER_A, "#{TEST}", IRCCaseMapping.RFC1459));
        assertFalse(InAppAlertVisibility.isConversationVisible(
                SERVER_A, "#[test]", SERVER_A, "#{TEST}", IRCCaseMapping.ASCII));
    }
}
