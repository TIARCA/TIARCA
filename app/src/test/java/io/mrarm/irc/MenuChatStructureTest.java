package io.mrarm.irc;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Regression tests for the chat toolbar/overflow menu structure. */
public class MenuChatStructureTest {

    @Test
    public void serverNicknameChangePrecedesUserModes() throws IOException {
        String xml = readMenu();
        assertOrdered(xml, "@+id/action_change_nickname", "@+id/action_user_modes");
    }

    @Test
    public void settingsImmediatelyPrecedesDisconnect() throws IOException {
        String xml = readMenu();
        String settings = "@+id/action_settings";
        String disconnect = "@+id/action_disconnect";
        int settingsIndex = xml.indexOf(settings);
        int disconnectIndex = xml.indexOf(disconnect);
        assertTrue("Settings item is missing", settingsIndex >= 0);
        assertTrue("Disconnect item is missing", disconnectIndex > settingsIndex);

        String between = xml.substring(settingsIndex + settings.length(), disconnectIndex);
        assertTrue("Settings must be immediately before Disconnect",
                !between.contains("<item"));
    }

    @Test
    public void callerIdAcceptToggleIsToolbarActionHiddenByDefault() throws IOException {
        String xml = readMenu();
        int start = xml.indexOf("@+id/action_callerid_accept_toggle");
        assertTrue("Caller-ID ACCEPT toggle is missing", start >= 0);
        int end = xml.indexOf("/>", start);
        assertTrue("Caller-ID ACCEPT toggle item is malformed", end > start);
        String item = xml.substring(start, end);
        assertTrue("Caller-ID ACCEPT toggle must be hidden until +g is active",
                item.contains("android:visible=\"false\""));
        assertTrue("Caller-ID ACCEPT toggle must be a toolbar action",
                item.contains("app:showAsAction=\"always\""));
    }

    private static void assertOrdered(String xml, String first, String second) {
        int firstIndex = xml.indexOf(first);
        int secondIndex = xml.indexOf(second);
        assertTrue(first + " is missing", firstIndex >= 0);
        assertTrue(second + " is missing", secondIndex >= 0);
        assertTrue(first + " must appear before " + second, firstIndex < secondIndex);
    }

    private static String readMenu() throws IOException {
        Path path = Paths.get("src/main/res/menu/menu_chat.xml");
        if (!Files.exists(path))
            path = Paths.get("app/src/main/res/menu/menu_chat.xml");
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
