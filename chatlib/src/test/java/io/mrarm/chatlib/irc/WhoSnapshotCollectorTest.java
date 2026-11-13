package io.mrarm.chatlib.irc;

import org.junit.Test;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class WhoSnapshotCollectorTest {
    @Test public void standardWhoIsCollectedTo315Without512Limit() {
        WhoSnapshotCollector collector = new WhoSnapshotCollector();
        AtomicReference<WhoSnapshotCollector.Result> result = new AtomicReference<>();
        assertTrue(collector.begin("#chat", "", result::set));
        for (int i = 0; i < 854; i++)
            collector.noteNumeric("352", Arrays.asList("me", "#chat", "u", "host.net",
                    "irc", "nick" + i, "H", "0 Test"));
        collector.noteNumeric("315", Arrays.asList("me", "#chat", "End of WHO"));
        assertNotNull(result.get());
        assertEquals(854, result.get().users.size());
        assertFalse(result.get().truncated);
        assertEquals("nick853!u@host.net", result.get().users.get(853).mask());
    }

    @Test public void whoxTokenAndChannelAreScoped() {
        WhoSnapshotCollector collector = new WhoSnapshotCollector();
        AtomicReference<WhoSnapshotCollector.Result> result = new AtomicReference<>();
        assertTrue(collector.begin("#chat", "842", result::set));
        collector.noteNumeric("354", Arrays.asList("me", "999", "u", "wrong", "no", "H", "0"));
        collector.noteNumeric("352", Arrays.asList("me", "#chat", "u", "wrong", "irc", "no", "H"));
        collector.noteNumeric("354", Arrays.asList("me", "842", "id", "cloak.net", "pippo", "H", "0"));
        collector.noteNumeric("315", Arrays.asList("me", "#other", "end"));
        assertNull(result.get());
        collector.noteNumeric("315", Arrays.asList("me", "#chat", "end"));
        assertEquals(1, result.get().users.size());
        assertEquals("pippo!id@cloak.net", result.get().users.get(0).mask());
    }

    @Test public void cannotRunTwoConcurrentQueriesOnOneNetwork() {
        WhoSnapshotCollector collector = new WhoSnapshotCollector();
        assertTrue(collector.begin("#one", "", result -> { }));
        assertFalse(collector.begin("#two", "", result -> { }));
        collector.cancel();
        assertTrue(collector.begin("#two", "", result -> { }));
    }
}
