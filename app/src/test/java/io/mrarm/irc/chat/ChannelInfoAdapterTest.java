package io.mrarm.irc.chat;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.mrarm.chatlib.dto.NickPrefixList;
import io.mrarm.chatlib.dto.NickWithPrefix;
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

    @Test
    public void memberUpdatesKeepSearchRowWhenThresholdDoesNotChange() {
        assertFalse(ChannelInfoAdapter.requiresFullRefresh(false, 30, 31));
        assertFalse(ChannelInfoAdapter.requiresFullRefresh(false, 100, 99));
        assertTrue(ChannelInfoAdapter.requiresFullRefresh(false, 29, 30));
        assertTrue(ChannelInfoAdapter.requiresFullRefresh(false, 30, 29));
        assertTrue(ChannelInfoAdapter.requiresFullRefresh(true, 100, 100));
    }

    @Test
    public void areMemberListEqualsIdentifiesEqualAndDifferentLists() {
        NickWithPrefix alice = new NickWithPrefix("Alice", null);
        NickWithPrefix bobOp = new NickWithPrefix("Bob", new NickPrefixList("@"));
        NickWithPrefix bobVoice = new NickWithPrefix("Bob", new NickPrefixList("+"));

        assertTrue(ChannelInfoAdapter.areMemberListEquals(
                Arrays.asList(alice, bobOp),
                Arrays.asList(new NickWithPrefix("Alice", null), new NickWithPrefix("Bob", new NickPrefixList("@")))
        ));

        assertFalse(ChannelInfoAdapter.areMemberListEquals(
                Arrays.asList(alice, bobOp),
                Arrays.asList(alice, bobVoice)
        ));

        assertFalse(ChannelInfoAdapter.areMemberListEquals(
                Arrays.asList(alice),
                Arrays.asList(alice, bobOp)
        ));

        assertTrue(ChannelInfoAdapter.areMemberListEquals(null, null));
        assertFalse(ChannelInfoAdapter.areMemberListEquals(Collections.singletonList(alice), null));
    }
}
