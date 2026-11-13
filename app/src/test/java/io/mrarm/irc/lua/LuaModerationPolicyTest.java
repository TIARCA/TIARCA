package io.mrarm.irc.lua;

import org.junit.Test;
import io.mrarm.chatlib.irc.IRCCaseMapping;
import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.chatlib.dto.NickPrefixList;
import static org.junit.Assert.*;

public class LuaModerationPolicyTest {
    private static final String SERVER = "2598f719-dfb6-4bad-a69c-ff78cecb9315";
    private LuaScriptStore.Script script(boolean users, boolean moderate) {
        return new LuaScriptStore.Script(
                "0a14a509-49e4-4d79-8884-9504e76a252f",
                "approved.lua", "hash", SERVER, true, false, users, moderate, "");
    }

    @Test public void moderationNeedsSeparateConsentAndExactTarget() {
        assertFalse(LuaModerationPolicy.scoped(script(false, false), SERVER,
                "#channel", "command", "#channel", true));
        assertTrue(LuaModerationPolicy.scoped(script(false, true), SERVER,
                "#channel", "command", "#channel", true));
        assertFalse(LuaModerationPolicy.scoped(script(false, true), SERVER,
                "#channel", "command", "#other", true));
        assertFalse(LuaModerationPolicy.scoped(script(false, true), SERVER,
                "#channel", "timer", "#channel", true));
        assertFalse(LuaModerationPolicy.scoped(script(false, true), SERVER,
                "Nick", "command", "Nick", true));
        assertFalse(LuaModerationPolicy.scoped(script(false, true), "other",
                "#channel", "command", "#channel", true));
    }

    @Test public void whoRequiresIndependentUserReadConsentAndLocalCommand() {
        assertTrue(LuaModerationPolicy.scoped(script(true, false), SERVER,
                "#room", "command", "#room", false));
        assertFalse(LuaModerationPolicy.scoped(script(false, true), SERVER,
                "#room", "command", "#room", false));
        assertFalse(LuaModerationPolicy.scoped(script(true, false), SERVER,
                "#room", "join", "#room", false));
    }

    @Test public void untrustedTokensCannotBecomeIrcParameters() {
        assertFalse(LuaModerationPolicy.validNick("evil\r\nMODE #test +o evil"));
        assertFalse(LuaModerationPolicy.validNick("#channel"));
        assertFalse(LuaModerationPolicy.validNick("a,b"));
        assertTrue(LuaModerationPolicy.validNick("NormalNick"));
        assertFalse(LuaModerationPolicy.validReason("a\nb"));
        assertFalse(LuaModerationPolicy.validReason("hi\001VERSION"));
        assertTrue(LuaModerationPolicy.validReason("A short reason"));
        assertTrue(LuaModerationPolicy.validBanHost("masked.example"));
        assertFalse(LuaModerationPolicy.validBanHost("*"));
        assertFalse(LuaModerationPolicy.validBanHost("host\r\n"));
        assertFalse(LuaModerationPolicy.permittedMode("+b"));
        assertFalse(LuaModerationPolicy.permittedMode("+q"));
        assertTrue(LuaModerationPolicy.permittedMode("+o"));
        assertTrue(LuaModerationPolicy.permittedMode("-v"));
    }

    @Test public void ircCaseMappingPreventsSelfModerationUnderRfc1459() {
        assertTrue(LuaModerationPolicy.isSelf(IRCCaseMapping.RFC1459,
                "Operator{", "Operator["));
        assertTrue(LuaModerationPolicy.isSelf(IRCCaseMapping.RFC1459,
                "Operator|", "Operator\\"));
        assertTrue(LuaModerationPolicy.isSelf(IRCCaseMapping.RFC1459,
                "Operator~", "Operator^"));
        assertFalse(LuaModerationPolicy.isSelf(IRCCaseMapping.STRICT_RFC1459,
                "Operator~", "Operator^"));
        assertFalse(LuaModerationPolicy.isSelf(IRCCaseMapping.ASCII,
                "Operator{", "Operator["));
        assertTrue(LuaModerationPolicy.isSelf(IRCCaseMapping.ASCII,
                "operator", "OPERATOR"));
    }

    @Test public void liveModerationGateRejectsLostOperatorOrTarget() {
        assertTrue(LuaModerationPolicy.liveTargetAllowed(
                true, true, true, false, "OtherNick"));
        assertFalse(LuaModerationPolicy.liveTargetAllowed(
                false, true, true, false, "OtherNick"));
        assertFalse(LuaModerationPolicy.liveTargetAllowed(
                true, false, true, false, "OtherNick"));
        assertFalse(LuaModerationPolicy.liveTargetAllowed(
                true, true, false, false, "OtherNick"));
        assertFalse(LuaModerationPolicy.liveTargetAllowed(
                true, true, true, true, "OtherNick"));
        assertFalse(LuaModerationPolicy.liveTargetAllowed(
                true, true, true, false, "OtherNick\r\nMODE"));
    }

    @Test public void automaticPunishmentSkipsAllPrivilegedOrUnknownUsers() {
        assertTrue(LuaModerationPolicy.unprivilegedTarget(
                new NickWithPrefix("regular", new NickPrefixList(""))));
        assertFalse(LuaModerationPolicy.unprivilegedTarget(
                new NickWithPrefix("operator", new NickPrefixList("@"))));
        assertFalse(LuaModerationPolicy.unprivilegedTarget(
                new NickWithPrefix("voice", new NickPrefixList("+"))));
        // NAMES parses an ordinary, unprefixed nickname as a null prefix list.
        assertTrue(LuaModerationPolicy.unprivilegedTarget(
                new NickWithPrefix("ordinary", null)));
        assertFalse(LuaModerationPolicy.unprivilegedTarget(null));
    }

    @Test public void blacklistOverrideRequiresVerifiedCurrentSenderAccount() {
        LuaScriptStore.Script approved = script(true, true);
        assertTrue(LuaModerationPolicy.blacklistOverrideAllowed(
                approved, SERVER, "message", "VoiceNick", "voicenick",
                "registered", "REGISTERED", true, IRCCaseMapping.ASCII));
        assertFalse(LuaModerationPolicy.blacklistOverrideAllowed(
                script(false, true), SERVER, "message", "Nick", "Nick",
                "registered", "registered", true, IRCCaseMapping.ASCII));
        assertFalse(LuaModerationPolicy.blacklistOverrideAllowed(
                script(true, false), SERVER, "message", "Nick", "Nick",
                "registered", "registered", true, IRCCaseMapping.ASCII));
        assertFalse(LuaModerationPolicy.blacklistOverrideAllowed(
                approved, "different-network", "message", "Nick", "Nick",
                "registered", "registered", true, IRCCaseMapping.ASCII));
        assertFalse(LuaModerationPolicy.blacklistOverrideAllowed(
                approved, SERVER, "command", "Nick", "Nick",
                "registered", "registered", true, IRCCaseMapping.ASCII));
        assertFalse(LuaModerationPolicy.blacklistOverrideAllowed(
                approved, SERVER, "message", "sender", "other",
                "registered", "registered", true, IRCCaseMapping.ASCII));
        assertFalse(LuaModerationPolicy.blacklistOverrideAllowed(
                approved, SERVER, "message", "Nick", "Nick",
                "registered", "other-account", true, IRCCaseMapping.ASCII));
        assertFalse(LuaModerationPolicy.blacklistOverrideAllowed(
                approved, SERVER, "message", "Nick", "Nick",
                "registered", null, true, IRCCaseMapping.ASCII));
        assertTrue(LuaModerationPolicy.blacklistOverrideAllowed(
                approved, SERVER, "message", "Nick", "Nick",
                "*", null, true, IRCCaseMapping.ASCII));
        assertFalse(LuaModerationPolicy.blacklistOverrideAllowed(
                approved, SERVER, "message", "Nick", "Nick",
                "*", null, false, IRCCaseMapping.ASCII));
        assertFalse(LuaModerationPolicy.blacklistOverrideAllowed(
                approved, SERVER, "message", "Nick", "Nick",
                "*", "registered", true, IRCCaseMapping.ASCII));
        assertFalse(LuaModerationPolicy.blacklistOverrideAllowed(
                approved, SERVER, "message", "Nick", "Nick",
                "acc\\r\\nMODE", "acc\\r\\nMODE", true, IRCCaseMapping.ASCII));
    }

    @Test public void revokedPermissionDisablesExistingScriptActions() {
        LuaScriptStore.Script disabled = new LuaScriptStore.Script(
                "0a14a509-49e4-4d79-8884-9504e76a252f",
                "approved.lua", "hash", SERVER, false, false, true, true, "");
        assertFalse(LuaModerationPolicy.scoped(disabled, SERVER,
                "#room", "command", "#room", true));
    }
}
