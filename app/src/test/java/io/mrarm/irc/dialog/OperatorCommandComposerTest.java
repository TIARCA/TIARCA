package io.mrarm.irc.dialog;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OperatorCommandComposerTest {
    private OperatorCommandComposer.State state() {
        OperatorCommandComposer.State s = new OperatorCommandComposer.State();
        s.channel = "#canale";
        s.nick = "nickname";
        s.maskNick = "nickname";
        s.ident = "ident";
        s.host = "host";
        s.account = "account";
        s.useHost = true;
        return s;
    }

    private OperatorCommandComposer.Dialect inspircd() {
        return new OperatorCommandComposer.Dialect(",mNuRGrjz", "R");
    }

    @Test public void combinesModeKickAndIndependentMatchingBans() {
        OperatorCommandComposer.State s = state();
        s.currentModes.put('v', true);
        s.wantedModes.put('v', false);
        s.kick = true;
        s.reason = "motivazione";
        s.ban = true;
        s.matching.add('R');
        s.matching.add('U');
        assertEquals(Arrays.asList(
                "MODE #canale -v nickname",
                "KICK #canale nickname :motivazione",
                "MODE #canale +b R:account",
                "MODE #canale +b U:*!*@host"
        ), OperatorCommandComposer.build(s, inspircd()).commands);
    }

    @Test public void combinesActingAndMatchingRatherThanAddingUnrelatedBans() {
        OperatorCommandComposer.State s = state();
        s.ban = true;
        s.acting.add('m');
        s.matching.add('U');
        OperatorCommandComposer.Result result =
                OperatorCommandComposer.build(s, inspircd());
        assertNull(result.error);
        assertEquals(Arrays.asList("MODE #canale +b m:U:*!*@host"), result.commands);
    }

    @Test public void supportsMultipleActsAndMatchingTargetsAsSeparateCompositions() {
        OperatorCommandComposer.State s = state();
        s.ban = true;
        s.acting.add('m');
        s.acting.add('N');
        s.matching.add('R');
        s.matching.add('U');
        assertEquals(4, OperatorCommandComposer.build(s, inspircd()).commands.size());
    }

    @Test public void timedBanUsesThreeHoursByDefault() {
        OperatorCommandComposer.State s = state();
        s.ban = true;
        s.timed = true;
        assertEquals("TBAN #canale 3h *!*@host",
                OperatorCommandComposer.build(s, inspircd()).commands.get(0));
    }

    @Test public void unrealUsesNamedAccountAndMinutes() {
        OperatorCommandComposer.Dialect unreal = new OperatorCommandComposer.Dialect(
                "~,quiet,nickchange,account,country,time", "account");
        OperatorCommandComposer.State s = state();
        s.ban = true;
        s.timed = true;
        s.acting.add('m');
        s.matching.add('R');
        assertEquals("MODE #canale +b ~time:180:~quiet:~account:account",
                OperatorCommandComposer.build(s, unreal).commands.get(0));
    }

    @Test public void rejectsUnrealUnauthenticatedHostSpecificBan() {
        OperatorCommandComposer.Dialect unreal = new OperatorCommandComposer.Dialect(
                "~,account,quiet", "account");
        OperatorCommandComposer.State s = state();
        s.matching.add('U');
        s.ban = true;
        assertNotNull(OperatorCommandComposer.build(s, unreal).error);
        s.useHost = false;
        s.useNick = false;
        s.useIdent = false;
        assertEquals("MODE #canale +b ~account:0",
                OperatorCommandComposer.build(s, unreal).commands.get(0));
    }

    @Test public void doesNotInventExtbansWhenServerDoesNotAdvertiseThem() {
        OperatorCommandComposer.State s = state();
        s.acting.add('m');
        assertNotNull(OperatorCommandComposer.build(s,
                new OperatorCommandComposer.Dialect(null, null)).error);
    }

    @Test public void preservesManualPreviewOrderAndRestrictsChannel() {
        OperatorCommandComposer.Result parsed = OperatorCommandComposer.parsePreview(
                "/kick #canale nickname :messaggio\n/mode #canale +b *!*@host", "#canale");
        assertNull(parsed.error);
        assertEquals(Arrays.asList("kick #canale nickname :messaggio",
                "mode #canale +b *!*@host"), parsed.commands);
        assertNotNull(OperatorCommandComposer.parsePreview(
                "/mode #altro +b *!*@host", "#canale").error);
        assertNotNull(OperatorCommandComposer.parsePreview(
                "/join #canale", "#canale").error);
        assertNotNull(OperatorCommandComposer.parsePreview(
                "/mode #canale +b *!*@host\rBAD", "#canale").error);
    }
}
