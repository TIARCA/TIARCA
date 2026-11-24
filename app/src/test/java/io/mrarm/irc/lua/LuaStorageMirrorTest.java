package io.mrarm.irc.lua;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class LuaStorageMirrorTest {
    private static final String ID = "1f1a37a1-b5be-44ef-a47d-2ba9980985cb";
    @Test public void onlyConfirmedRevisionsAreReused() {
        LuaStorageMirror mirror = new LuaStorageMirror();
        assertNull(mirror.resolve(ID, 1, true, null));
        byte[][] first = mirror.resolve(ID, 1, true, Arrays.asList("count", "1"));
        assertNotNull(first);
        for (int i = 0; i < 1000; i++)
            assertSame(first, mirror.resolve(ID, 1, true, null));
        assertNull(mirror.resolve(ID, 1, true, Arrays.asList("count", "2")));
        assertNull(mirror.resolve(ID, 2, true, null));
        byte[][] second = mirror.resolve(ID, 2, true, Arrays.asList("count", "2"));
        assertNotSame(first, second);
        assertNull(mirror.resolve(ID, 1, true, Arrays.asList("count", "1")));
        assertNull(mirror.resolve(ID, 3, true, Arrays.asList("count", "1", "count", "2")));
        assertEquals(0, mirror.resolve(ID, -1, false, Collections.emptyList()).length);
        assertNull(mirror.resolve(ID, 2, true, null));
        assertNotNull(mirror.resolve(ID, 3, true, Arrays.asList("count", "3")));
        mirror.clear();
        assertNull(mirror.resolve(ID, 3, true, null));
    }
}
