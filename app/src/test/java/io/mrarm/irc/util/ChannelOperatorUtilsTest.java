package io.mrarm.irc.util;

import org.junit.Test;

import io.mrarm.chatlib.dto.NickPrefixList;
import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.chatlib.irc.IRCConnection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChannelOperatorUtilsTest {

    @Test
    public void hasVoiceReturnsTrueWhenPrefixContainsPlus() {
        NickWithPrefix memberVoice = new NickWithPrefix("Pippo", new NickPrefixList("+"));
        assertTrue(ChannelOperatorUtils.hasVoice(null, memberVoice));
    }

    @Test
    public void hasVoiceReturnsTrueForOpAndVoice() {
        NickWithPrefix memberOpVoice = new NickWithPrefix("Pippo", new NickPrefixList("@+"));
        assertTrue(ChannelOperatorUtils.hasVoice(null, memberOpVoice));
    }

    @Test
    public void hasVoiceReturnsFalseForOpOnly() {
        NickWithPrefix memberOp = new NickWithPrefix("Pippo", new NickPrefixList("@"));
        assertFalse(ChannelOperatorUtils.hasVoice(null, memberOp));
    }

    @Test
    public void hasVoiceReturnsFalseForNoPrefix() {
        NickWithPrefix memberNormal = new NickWithPrefix("Pippo", null);
        assertFalse(ChannelOperatorUtils.hasVoice(null, memberNormal));
    }
}
