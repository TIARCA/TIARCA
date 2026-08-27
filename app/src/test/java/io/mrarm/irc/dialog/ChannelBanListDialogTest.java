package io.mrarm.irc.dialog;

import org.junit.Test;

import io.mrarm.irc.irc.BanListCommandHandler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChannelBanListDialogTest {

    private static final long NOW = 1_000_000L;

    private BanListCommandHandler.Entry entry(String mask, long ageHours) {
        return entryAtAge(mask, ageHours * 60L * 60L);
    }

    private BanListCommandHandler.Entry entryAtAge(String mask, long ageSeconds) {
        return new BanListCommandHandler.Entry(mask, "operator", NOW - ageSeconds);
    }

    @Test
    public void includesHostOnlyAndMuteMasksInWindow() {
        assertTrue(ChannelBanListDialog.isCleanupCandidate(entry("*!*@example.host", 60), NOW));
        assertTrue(ChannelBanListDialog.isCleanupCandidate(entry("nickname!*@example.host", 60), NOW));
        assertTrue(ChannelBanListDialog.isCleanupCandidate(entry("m:*!*@example.host", 60), NOW));
    }

    @Test
    public void excludesProtectedUserAndUnknownExtbans() {
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("j:*!*@example.host", 60), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("R:registered-account", 60), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("a:account", 60), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("u:*!ident@*", 60), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("u:*!*@example.host", 60), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("u:*!ident@example.host", 60), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("u:*!*@*", 60), NOW));
    }

    @Test
    public void excludesIdentMasks() {
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("*!ident@*", 60), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("*!ident@example.host", 60), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("nickname!ident@example.host", 60), NOW));
    }

    @Test
    public void includesOnlyBansInTheInclusive48To72HourWindow() {
        assertFalse(ChannelBanListDialog.isCleanupCandidate(
                entryAtAge("*!*@example.host", 48L * 60L * 60L - 60L), NOW));
        assertTrue(ChannelBanListDialog.isCleanupCandidate(entry("*!*@example.host", 48), NOW));
        assertTrue(ChannelBanListDialog.isCleanupCandidate(entry("*!*@example.host", 60), NOW));
        assertTrue(ChannelBanListDialog.isCleanupCandidate(entry("*!*@example.host", 72), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(
                entryAtAge("*!*@example.host", 72L * 60L * 60L + 1L), NOW));
    }

    @Test
    public void excludesUnknownDatesAndNickOnlyMasks() {
        assertFalse(ChannelBanListDialog.isCleanupCandidate(
                new BanListCommandHandler.Entry("*!*@example.host", "operator", 0), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("*!*@*", 60), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("nickname!*@*", 60), NOW));
    }

    @Test
    public void searchMatchesMaskAndAuthorCaseInsensitively() {
        BanListCommandHandler.Entry entry = new BanListCommandHandler.Entry(
                "*!*@*.as62651.net", "hub-de.Simosnap.com", NOW);

        assertTrue(ChannelBanListDialog.matchesSearch(entry, "62651.NET"));
        assertTrue(ChannelBanListDialog.matchesSearch(entry, "HUB-DE.SIMOSNAP"));
        assertFalse(ChannelBanListDialog.matchesSearch(entry, "unrelated"));
    }

    @Test
    public void searchDoesNotMatchTheDateAndEmptySearchShowsAllRows() {
        BanListCommandHandler.Entry entry = new BanListCommandHandler.Entry(
                "*!*@example.net", "PrincipeDelB", NOW);

        assertTrue(ChannelBanListDialog.matchesSearch(entry, ""));
        assertTrue(ChannelBanListDialog.matchesSearch(entry, "  "));
        assertTrue(ChannelBanListDialog.matchesSearch(entry, "principe"));
        assertFalse(ChannelBanListDialog.matchesSearch(entry, Long.toString(NOW)));
    }
}
