package io.mrarm.irc.lua;

import org.junit.Test;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import io.mrarm.chatlib.irc.WhoSnapshotCollector;
import static org.junit.Assert.*;

/** The 854/523 case must not be constrained by the passive identity cache. */
public class MaskFinderLargeChannelTest {
    @Test public void matches523UsersOutOf854WithoutDroppingTheRest() {
        WhoSnapshotCollector collector = new WhoSnapshotCollector();
        AtomicReference<WhoSnapshotCollector.Result> response = new AtomicReference<>();
        assertTrue(collector.begin("#amicizia", "", response::set));
        for (int i = 0; i < 854; i++)
            collector.noteNumeric("352", Arrays.asList("me", "#amicizia", "ident",
                    i < 523 ? "pippo.example.net" : "other.example.net",
                    "irc.example.net", "Nick" + i, "H", "0 realname"));
        collector.noteNumeric("315", Arrays.asList("me", "#amicizia", "End of WHO"));
        assertNotNull(response.get());
        assertEquals(854, response.get().users.size());
        int matches = 0;
        for (WhoSnapshotCollector.User u : response.get().users)
            if (MaskFinderMatcher.matches("pippo", u.mask())) matches++;
        assertEquals(523, matches);
        assertFalse(response.get().truncated);
    }
}
