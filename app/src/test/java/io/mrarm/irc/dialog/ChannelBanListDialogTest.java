package io.mrarm.irc.dialog;

import org.junit.Test;

import io.mrarm.irc.irc.BanListCommandHandler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChannelBanListDialogTest {

    private static final long NOW = 1_000_000L;

    private BanListCommandHandler.Entry entry(String mask, long ageHours) {
        return new BanListCommandHandler.Entry(mask, "operator",
                NOW - ageHours * 60L * 60L);
    }

    @Test
    public void includesHostOnlyAndMuteMasksInWindow() {
        assertTrue(ChannelBanListDialog.isCleanupCandidate(entry("*!*@example.host", 12), NOW));
        assertTrue(ChannelBanListDialog.isCleanupCandidate(entry("nickname!*@example.host", 12), NOW));
        assertTrue(ChannelBanListDialog.isCleanupCandidate(entry("m:*!*@example.host", 12), NOW));
    }

    @Test
    public void excludesProtectedUserAndUnknownExtbans() {
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("j:*!*@example.host", 12), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("R:registered-account", 12), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("a:account", 12), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("u:*!ident@*", 12), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("u:*!*@example.host", 12), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("u:*!ident@example.host", 12), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("u:*!*@*", 12), NOW));
    }

    @Test
    public void excludesIdentMasks() {
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("*!ident@*", 12), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("*!ident@example.host", 12), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("nickname!ident@example.host", 12), NOW));
    }

    @Test
    public void excludesWrongAgeUnknownDatesAndNickOnlyMasks() {
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("*!*@example.host", 5), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("*!*@example.host", 31), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(
                new BanListCommandHandler.Entry("*!*@example.host", "operator", 0), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("*!*@*", 12), NOW));
        assertFalse(ChannelBanListDialog.isCleanupCandidate(entry("nickname!*@*", 12), NOW));
    }
}
