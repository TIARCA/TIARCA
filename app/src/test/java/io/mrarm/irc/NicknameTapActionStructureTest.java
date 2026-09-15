package io.mrarm.irc;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Regression coverage for TIARCA-026 nickname tap behavior and settings wiring. */
public class NicknameTapActionStructureTest {

    @Test
    public void defaultKeepsExistingUserDetailsBehavior() throws IOException {
        String settings = read("app/settings.yml", "settings.yml");
        assertTrue(settings.contains("String nickTapAction = \"details\""));
        assertTrue(settings.contains("enum: [\"details\", \"insert_nick\", \"private\"]"));
    }

    @Test
    public void interfaceExposesChatInteractionChoice() throws IOException {
        String source = read(
                "app/src/main/java/io/mrarm/irc/setting/fragment/InterfaceSettingsFragment.java",
                "src/main/java/io/mrarm/irc/setting/fragment/InterfaceSettingsFragment.java");
        assertTrue(source.contains("pref_header_chat_interactions"));
        assertTrue(source.contains("PREF_NICK_TAP_ACTION"));
    }

    @Test
    public void nicknameTapCanReuseTabCompletionSemantics() throws IOException {
        String editText = read(
                "app/src/main/java/io/mrarm/irc/view/ChatAutoCompleteEditText.java",
                "src/main/java/io/mrarm/irc/view/ChatAutoCompleteEditText.java");
        assertTrue(editText.contains("terminateNickToken(nick.trim())"));
        assertTrue(editText.contains("getText().replace(start, end, value)"));

        String adapter = read(
                "app/src/main/java/io/mrarm/irc/chat/ChatMessagesAdapter.java",
                "src/main/java/io/mrarm/irc/chat/ChatMessagesAdapter.java");
        assertTrue(adapter.contains("handleNicknameTap(widget, nick)"));
        assertTrue(adapter.contains("insertNicknameAsTabCompletion(nick)"));
        assertTrue(adapter.contains("openDirectConversationForSharing"));
    }

    private static String read(String rootPath, String modulePath) throws IOException {
        Path path = Paths.get(rootPath);
        if (!Files.exists(path))
            path = Paths.get(modulePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
