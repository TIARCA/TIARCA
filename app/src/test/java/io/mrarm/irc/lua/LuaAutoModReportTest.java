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
        assertTrue(message.endsWith(", AVVISO"));
        assertFalse(message.contains("richiesto"));
        assertFalse(message.contains("esito non confermato"));
    }

    @Test public void escalationDecisionsAreDistinguishedFromSentCommands() {
        assertTrue(render("AutoMod test-ser/#room Nick-2 85.2% infrazione 2 (kick)")
                .contains("KICK"));
        assertTrue(render("AutoMod test-ser/#room Nick-2 100.0% infrazione 3 (tbankick)")
                .contains("KICK + BAN TEMPORANEO"));
        assertTrue(render("AutoMod test-ser/#room Nick-2 99.9% infrazione 4 (bankick)")
                .contains("KICK + BAN"));
        assertTrue(render("AutoMod test-ser/#room Nick-2 80.0% infrazione 1 (monitor)")
                .contains("MONITORAGGIO"));
    }

    @Test public void autoModBUsesConcentrationAndTimedMuteLabel() {
        String notice = render("AutoModB test-ser/#room Nick-2 66.7% infrazione 1 (avviso)");
        assertNotNull(notice);
        assertTrue(notice.contains("concentrazione 66.7%"));
        assertTrue(notice.endsWith("AVVISO"));
        assertTrue(render("AutoModB test-ser/#room Nick-2 80.0% infrazione 2 (tmute)")
                .contains("MUTE TEMPORANEO"));
        assertNull(render("AutoModB wrong-id/#room Nick-2 80.0% infrazione 2 (tmute)"));
    }

    @Test public void autoModV4UsesBinaryAdClassificationNotConcentration() {
        String ad = render("AutoModV4 test-ser/#room Nick-2 100.0% infrazione 1 (monitor) motore B");
        assertNotNull(ad);
        assertTrue(ad.contains("annunci riconosciuti"));
        assertFalse(ad.contains("concentrazione"));
        assertTrue(ad.contains("MONITORAGGIO"));
        String generic = render("AutoModV4 test-ser/#room Nick-2 75.0% infrazione 3 (tmute) motore A");
        assertNotNull(generic);
        assertTrue(generic.contains("ripetizione 75.0%"));
        assertTrue(generic.contains("MUTE TEMPORANEO"));
    }

    @Test public void v4EvidenceSuffixDoesNotChangeTheNotice() {
        String report = "AutoModV4 test-ser/#room Nick-2 75.0% infrazione 3 (tmute) motore A";
        assertEquals(render(report), render(report + " rule SIMILAR score 3 threshold 75 count 3 refs 00000000-0000-0000-0000-000000000000:1,00000000-0000-0000-0000-000000000000:2"));
        assertNull(render(report + " rule INVALID! score 3 threshold 75 count 3 refs -"));
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
