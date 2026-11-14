package io.mrarm.irc.lua;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class MaskFinderStatusMessageInfoTest {
    @Test public void accountAppearsAfterMaskOnlyWhenPresent() {
        MaskFinderStatusMessageInfo message = MaskFinderStatusMessageInfo.batch(
                "#chat", 1L, Arrays.asList("Alice", "Bob"),
                Arrays.asList("Alice!ident@host.net", "Bob!ident@other.net"),
                Arrays.asList("alice_account", null));
        assertEquals("Alice!ident@host.net R:alice_account\n" +
                "Bob!ident@other.net", message.getMessage());
        assertEquals(Arrays.asList("Alice", "Bob"), message.nicks);
        assertEquals(Arrays.asList("Alice!ident@host.net", "Bob!ident@other.net"),
                message.masks);
    }

    @Test(expected = IllegalArgumentException.class)
    public void accountListMustMatchResultCount() {
        MaskFinderStatusMessageInfo.batch("#chat", 1L,
                Arrays.asList("Alice"), Arrays.asList("Alice!id@host"),
                Arrays.asList("alice", "other"));
    }
}
