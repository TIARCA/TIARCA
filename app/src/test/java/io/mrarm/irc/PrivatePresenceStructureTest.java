package io.mrarm.irc;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.assertTrue;

public class PrivatePresenceStructureTest {
    private static String read(String full, String module) throws Exception {
        Path path = Paths.get(full);
        if (!Files.exists(path)) path = Paths.get(module);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test public void preferenceIsIndependentFromChannelEventFilter() throws Exception {
        String settings = read("app/settings.yml", "settings.yml");
        assertTrue(settings.contains("privatePresenceEventsEnabled = true"));
        String fragment = read("app/src/main/java/io/mrarm/irc/chat/ChatMessagesFragment.java",
                "src/main/java/io/mrarm/irc/chat/ChatMessagesFragment.java");
        assertTrue(fragment.contains("isPrivateConversation() ? !ChatSettings.shouldShowPrivatePresenceEvents()"));
        assertTrue(fragment.contains("ChatSettings.PREF_PRIVATE_PRESENCE_EVENTS_ENABLED"));
        String ui = read("app/src/main/java/io/mrarm/irc/setting/fragment/InterfaceSettingsFragment.java",
                "src/main/java/io/mrarm/irc/setting/fragment/InterfaceSettingsFragment.java");
        assertTrue(ui.contains("PREF_PRIVATE_PRESENCE_EVENTS_ENABLED"));
    }

    @Test public void remoteQuitReusesNormalHandlerAndKeepsPvtTab() throws Exception {
        String wrapper = read("app/src/main/java/io/mrarm/irc/irc/PrivateQuitCommandHandler.java",
                "src/main/java/io/mrarm/irc/irc/PrivateQuitCommandHandler.java");
        assertTrue(wrapper.contains("delegate.handle(connection, sender, command, params, tags)"));
        assertTrue(wrapper.contains("listener.onPrivateQuit(connection, sender"));
        assertTrue(wrapper.indexOf("listener.onPrivateQuit(connection, sender") <
                wrapper.indexOf("delegate.handle(connection, sender, command, params, tags)"));
        String server = read("app/src/main/java/io/mrarm/irc/ServerConnectionInfo.java",
                "src/main/java/io/mrarm/irc/ServerConnectionInfo.java");
        assertTrue(server.contains("findOpenPrivateConversation(nick, data)"));
        assertTrue(server.contains("PRIVATE_PRESENCE_OFFLINE"));
    }
}
