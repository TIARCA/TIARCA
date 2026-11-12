package io.mrarm.irc.lua;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class LuaScriptSecurityTest {
    private static final String SCRIPT_ID = "0a14a509-49e4-4d79-8884-9504e76a252f";
    private static final String SERVER_ID = "2598f719-dfb6-4bad-a69c-ff78cecb9315";
    private static final String OTHER_SERVER = "22d1fa48-7d17-4cab-a34c-88d63cd9885a";

    private LuaScriptStore.Script approved(boolean send) {
        return new LuaScriptStore.Script(SCRIPT_ID, "example.lua", "a", SERVER_ID,
                true, send, "");
    }

    @Test public void repliesRequireSeparateSendConsent() {
        assertFalse(LuaActionPolicy.permitsSend(approved(false), SERVER_ID, "#chat",
                "message", "#chat", "Hello"));
        assertTrue(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "#chat",
                "message", "#chat", "Hello"));
    }

    @Test public void privateReplyIsAllowedOnlyWithSendConsent() {
        assertFalse(LuaActionPolicy.permitsSend(approved(false), SERVER_ID, "M99",
                "message", "M99", "bello ciao"));
        assertTrue(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "M99",
                "message", "M99", "bello ciao"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "M99",
                "message", "otherNick", "bello ciao"));
    }

    @Test public void presenceHooksNeedSendConsentAndStayInTheirIrcChannel() {
        assertTrue(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "#room",
                "join", "#room", "Benvenuto!"));
        assertFalse(LuaActionPolicy.permitsSend(approved(false), SERVER_ID, "#room",
                "join", "#room", "Benvenuto!"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "#room",
                "join", "#other", "Benvenuto!"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "OtherNick",
                "join", "OtherNick", "Ciao"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), OTHER_SERVER, "#room",
                "join", "#room", "Ciao"));
    }

    @Test public void localCommandCannotBypassSendPermissionOrTarget() {
        assertFalse(LuaActionPolicy.permitsSend(approved(false), SERVER_ID, "#chat",
                "command", "#chat", "Ciao"));
        assertTrue(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "#chat",
                "command", "#chat", "Ciao"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), OTHER_SERVER, "#chat",
                "command", "#chat", "Ciao"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "#chat",
                "command", "#other", "Ciao"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "",
                "command", "", "Ciao"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "#chat",
                "command", "#chat", "Line1\nLine2"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "#chat",
                "unknown", "#chat", "Ciao"));
    }

    @Test public void scopeCannotBeChangedByScript() {
        assertFalse(LuaActionPolicy.permitsSend(approved(true), OTHER_SERVER, "#chat",
                "message", "#chat", "Hello"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "#chat",
                "message", "#other", "Hello"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "#chat",
                "message", "#other", "Hello"));
        // Even if the inbound target were malformed, it must not be used as
        // a multi-recipient or CRLF-injected outgoing target.
        assertFalse(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "#a,#b",
                "message", "#a,#b", "Hello"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "#a\r\n",
                "message", "#a\r\n", "Hello"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "#chat",
                "connected", "#chat", "Hello"));
        assertFalse(LuaActionPolicy.permitsSend(approved(true), SERVER_ID, "",
                "message", "", "Hello"));
    }

    @Test public void retiredOrFaultyScriptCannotSend() {
        LuaScriptStore.Script disabled = new LuaScriptStore.Script(
                SCRIPT_ID, "example.lua", "a", SERVER_ID, false, true, "");
        assertFalse(LuaActionPolicy.permitsSend(disabled, SERVER_ID, "#chat",
                "message", "#chat", "Hello"));
    }

    @Test public void controlSequencesAndOverlongMessagesAreBlocked() {
        String[] bad = {"line1\nPRIVMSG #other :oops", "\r", "\0",
                "\001VERSION\001", "", "x".repeat(401), "é".repeat(201)};
        for (String text : bad)
            assertFalse("Must reject unsafe value", LuaActionPolicy.permitsSend(
                    approved(true), SERVER_ID, "#chat", "message", "#chat", text));
    }

    @Test public void rejectsBadUtf8AndEmbeddedNull() throws Exception {
        try {
            LuaScriptStore.checkedSource(new byte[]{(byte)0xc3, (byte)0x28});
            fail("Invalid UTF-8 accepted");
        } catch (IOException expected) { }
        try {
            LuaScriptStore.checkedSource("result = 1\0".getBytes(StandardCharsets.UTF_8));
            fail("Embedded NUL accepted");
        } catch (IOException expected) { }
    }

    @Test public void rejectsOversizedInputs() throws Exception {
        try {
            LuaScriptStore.readBounded(new ByteArrayInputStream(new byte[8193]), 8192);
            fail("Oversized Lua script accepted");
        } catch (IOException expected) { }
        assertEquals(8192, LuaScriptStore.readBounded(
                new ByteArrayInputStream(new byte[8192]), 8192).length);
    }
}
