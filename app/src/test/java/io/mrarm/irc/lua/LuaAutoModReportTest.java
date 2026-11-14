package io.mrarm.irc.lua;

import org.junit.Test;

import static org.junit.Assert.*;

public class LuaAutoModReportTest {
    private static final String SERVER = "test-server";
    private static final String CHANNEL = "#room";
    private static final String NICK = "Nick-2";

    private String render(String text) {
        return LuaAutoModReport.fromEventLog("message", SERVER, CHANNEL, NICK, text);
    }

    @Test public void warningIsShownLocallyEvenWithoutKickOrBan() {
        String message = render(
                "AutoMod test-ser/#room Nick-2 100.0% infrazione 1 (avviso)");
        assertNotNull(message);
        assertTrue(message.contains("#room - Nick-2"));
        assertTrue(message.contains("AVVISO richiesto"));
        assertTrue(message.contains("esito non confermato"));
    }

    @Test public void escalationDecisionsAreDistinguishedFromSentCommands() {
        assertTrue(render("AutoMod test-ser/#room Nick-2 85.2% infrazione 2 (kick)")
                .contains("KICK richiesto"));
        assertTrue(render("AutoMod test-ser/#room Nick-2 100.0% infrazione 3 (tbankick)")
                .contains("KICK + BAN temporaneo richiesti"));
        assertTrue(render("AutoMod test-ser/#room Nick-2 99.9% infrazione 4 (bankick)")
                .contains("KICK + BAN richiesti"));
        assertTrue(render("AutoMod test-ser/#room Nick-2 80.0% infrazione 1 (monitor)")
                .contains("solo monitoraggio"));
    }

    @Test public void unrelatedOrForgedLogsAreNotInjectedIntoServerTab() {
        assertNull(render("AutoMod: storico persistente pieno"));
        assertNull(render("Other script called moderation"));
        assertNull(render("AutoMod wrong-id/#room Nick-2 100.0% infrazione 1 (avviso)"));
        assertNull(render("AutoMod test-ser/#other Nick-2 100.0% infrazione 1 (avviso)"));
        assertNull(render("AutoMod test-ser/#room WrongNick 100.0% infrazione 1 (avviso)"));
        assertNull(render("AutoMod test-ser/#room Nick-2 100.0% infrazione 1 (invalid)"));
        assertNull(LuaAutoModReport.fromEventLog("timer", SERVER, CHANNEL, NICK,
                "AutoMod test-ser/#room Nick-2 100.0% infrazione 1 (avviso)"));
    }
}
