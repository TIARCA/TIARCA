package io.mrarm.irc;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.irc.ServerSupportList;

import static org.junit.Assert.*;

/** Checks private-query matching against live IRC state rather than drawer snapshots. */
public class PrivatePresenceTargetTest {
    private final ServerConnectionData data = new ServerConnectionData();

    @Test public void livePvtCanBeResolvedEvenWhenUiChannelSnapshotIsEmpty() {
        data.onChannelJoined("#amicizia");
        data.onChannelJoined("MimancaUnVenerdi");
        assertEquals("MimancaUnVenerdi",
                PrivatePresenceTarget.find("mimancaunvenerdi",
                        data.getJoinedChannelList(), data.getSupportList()));
        assertNull(PrivatePresenceTarget.find("mimancaunvenerdi",
                Collections.emptyList(), data.getSupportList()));
    }

    @Test public void neverTreatAChannelOrUnrelatedNickAsPrivateTarget() {
        ServerSupportList support = data.getSupportList();
        assertNull(PrivatePresenceTarget.find("#amicizia",
                Arrays.asList("#amicizia", "Alice"), support));
        assertNull(PrivatePresenceTarget.find("Alice", Arrays.asList("#Alice"), support));
        assertNull(PrivatePresenceTarget.find("Alicia", Arrays.asList("Alice"), support));
        assertNull(PrivatePresenceTarget.find("Alice", null, support));
    }

    @Test public void usesIrcCaseMappingForPrivateNames() {
        assertEquals("Nick[One]", PrivatePresenceTarget.find("nick{one}",
                Arrays.asList("#canale", "Nick[One]"), data.getSupportList()));
    }
}
