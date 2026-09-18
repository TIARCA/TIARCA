package io.mrarm.irc.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChatFragmentTabCloseTest {

    @Test
    public void closingRightmostSelectsImmediateLeftNeighbor() {
        assertEquals(3, ChatFragment.targetPositionAfterClose(4));
    }

    @Test
    public void closingMiddleTabSelectsImmediateLeftNeighbor() {
        assertEquals(2, ChatFragment.targetPositionAfterClose(3));
    }

    @Test
    public void closingFirstConversationFallsBackToServerTab() {
        assertEquals(0, ChatFragment.targetPositionAfterClose(1));
    }
}
