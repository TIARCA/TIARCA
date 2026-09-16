package io.mrarm.irc;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Regression coverage for TIARCA-025 jump-to-latest pagination invalidation. */
public class JumpToLatestStructureTest {

    @Test
    public void fullReloadInvalidatesStalePaginationWindow() throws IOException {
        String source = readChatMessagesFragment();
        assertTrue(source.contains("final int generation = ++mMessageWindowGeneration;"));
        assertTrue(source.contains("mLoadOlderIdentifier = null;"));
        assertTrue(source.contains("mLoadNewerIdentifier = null;"));
        assertTrue(source.contains("generation != mMessageWindowGeneration"));
        assertTrue(source.contains(
                "mLoadNewerIdentifier = nearMessage == null ? null : messages.getNewer();"));
    }

    @Test
    public void jumpToLatestStillUsesFreshLatestWindowWhenNewerHistoryExists()
            throws IOException {
        String source = readChatMessagesFragment();
        assertTrue(source.contains(
                "if (mLoadNewerIdentifier != null)\n            reloadMessages(null);"));
    }

    private static String readChatMessagesFragment() throws IOException {
        Path path = Paths.get(
                "app/src/main/java/io/mrarm/irc/chat/ChatMessagesFragment.java");
        if (!Files.exists(path))
            path = Paths.get(
                    "src/main/java/io/mrarm/irc/chat/ChatMessagesFragment.java");
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
