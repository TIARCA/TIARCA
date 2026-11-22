package io.mrarm.irc.lua;

import io.mrarm.irc.dialog.OperatorCommandComposer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LuaTimedMutePolicyTest {
    @Test public void inspUsesServerManagedTbanWithActingMuteHostMask() {
        OperatorCommandComposer.Dialect dialect =
                new OperatorCommandComposer.Dialect(",mNuURGrjz", "R");
        assertEquals("TBAN #canale 2m m:*!*@host.example",
                LuaModerationPolicy.timedMuteCommand(dialect,
                        "#canale", "2m", "host.example"));
        assertEquals("TBAN #canale 3h m:*!*@host.example",
                LuaModerationPolicy.timedMuteCommand(dialect,
                        "#canale", "3h", "host.example"));
    }

    @Test public void unrealOnlyUsesAdvertisedQuietAndTimedExtbans() {
        OperatorCommandComposer.Dialect dialect =
                new OperatorCommandComposer.Dialect("~,quiet,time,account", "");
        assertEquals("MODE #canale +b ~time:2:~quiet:*!*@host.example",
                LuaModerationPolicy.timedMuteCommand(dialect,
                        "#canale", "2m", "host.example"));
        assertNull(LuaModerationPolicy.timedMuteCommand(
                new OperatorCommandComposer.Dialect("~,time,account", ""),
                "#canale", "2m", "host.example"));
    }

    @Test public void unsupportedOrUnsafeRequestsFailClosed() {
        OperatorCommandComposer.Dialect dialect =
                new OperatorCommandComposer.Dialect(",mNuURGrjz", "R");
        assertNull(LuaModerationPolicy.timedMuteCommand(dialect,
                "#canale", "0m", "host.example"));
        assertNull(LuaModerationPolicy.timedMuteCommand(dialect,
                "#canale", "100m", "host.example"));
        assertNull(LuaModerationPolicy.timedMuteCommand(dialect,
                "#canale", "3h", "*.provider.net"));
        assertNull(LuaModerationPolicy.timedMuteCommand(
                new OperatorCommandComposer.Dialect(null, null),
                "#canale", "2m", "host.example"));
    }
}
