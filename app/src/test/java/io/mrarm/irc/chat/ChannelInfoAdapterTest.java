package io.mrarm.irc.chat;

import org.junit.Test;

import io.mrarm.chatlib.irc.IRCCaseMapping;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChannelInfoAdapterTest {

    @Test
    public void memberSearchIsShownOnlyForChannelsWithAtLeastThirtyMembers() {
        assertFalse(ChannelInfoAdapter.isMemberSearchVisible(29));
        assertTrue(ChannelInfoAdapter.isMemberSearchVisible(30));
    }

    @Test
    public void memberSearchUsesCaseInsensitiveSubstringMatching() {
        assertTrue(ChannelInfoAdapter.matchesMemberSearch("Resilienza_out", "resi",
                IRCCaseMapping.RFC1459));
        assertTrue(ChannelInfoAdapter.matchesMemberSearch("Mattia_03", "MATT",
                IRCCaseMapping.ASCII));
        assertFalse(ChannelInfoAdapter.matchesMemberSearch("Luna", "resi",
                IRCCaseMapping.RFC1459));
    }

    @Test
    public void memberSearchRespectsRfc1459CaseMapping() {
        assertTrue(ChannelInfoAdapter.matchesMemberSearch("[User]", "{user}",
                IRCCaseMapping.RFC1459));
        assertFalse(ChannelInfoAdapter.matchesMemberSearch("[User]", "{user}",
                IRCCaseMapping.ASCII));
    }
}
