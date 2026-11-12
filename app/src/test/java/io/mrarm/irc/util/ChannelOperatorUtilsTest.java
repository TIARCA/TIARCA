package io.mrarm.irc.util;

import org.junit.Test;

import io.mrarm.chatlib.dto.NickPrefixList;
import io.mrarm.chatlib.dto.ModeList;
import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.chatlib.irc.IRCConnection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChannelOperatorUtilsTest {

    @Test
    public void advertisedModesNotDecorativeGlyphsDetermineOperatorPrivilege() {
        NickPrefixList familiar = new NickPrefixList("@+");
        ModeList standardModes = new ModeList("ov");
        assertTrue(ChannelOperatorUtils.hasOperatorPrefix(new NickPrefixList("@"),
                new ModeList(familiar.toString()), standardModes, false));
        assertFalse(ChannelOperatorUtils.hasOperatorPrefix(new NickPrefixList("+"),
                new ModeList(familiar.toString()), standardModes, false));

        // On a custom IRCd '@' could represent voice, not operator.
        assertFalse(ChannelOperatorUtils.hasOperatorPrefix(new NickPrefixList("@"),
                new ModeList("@+"), new ModeList("vo"), false));
        assertTrue(ChannelOperatorUtils.hasOperatorPrefix(new NickPrefixList("+"),
                new ModeList("@+"), new ModeList("vo"), false));
        assertFalse(ChannelOperatorUtils.hasOperatorPrefix(new NickPrefixList("@"),
                new ModeList("+"), new ModeList("v"), false));
        assertFalse(ChannelOperatorUtils.hasOperatorPrefix(new NickPrefixList("%"),
                new ModeList("%@"), new ModeList("ho"), false));
        assertTrue(ChannelOperatorUtils.hasOperatorPrefix(new NickPrefixList("%"),
                new ModeList("%@"), new ModeList("ho"), true));
        assertTrue(ChannelOperatorUtils.hasOperatorPrefix(new NickPrefixList("~"),
                new ModeList("~@+"), new ModeList("qov"), false));
        assertFalse(ChannelOperatorUtils.hasOperatorPrefix(null, standardModes,
                standardModes, false));
    }

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
