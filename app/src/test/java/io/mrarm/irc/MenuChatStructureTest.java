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
    public void serverNicknameChangeImmediatelyPrecedesUserModes() throws IOException {
        String xml = readMenu();
        assertImmediatelyOrdered(xml, "@+id/action_change_nickname", "@+id/action_user_modes");
    }

    @Test
    public void settingsImmediatelyPrecedesDisconnect() throws IOException {
        String xml = readMenu();
        assertImmediatelyOrdered(xml, "@+id/action_settings", "@+id/action_disconnect");
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

    private static void assertImmediatelyOrdered(String xml, String first, String second) {
        int firstIndex = xml.indexOf(first);
        int secondIndex = xml.indexOf(second);
        assertTrue(first + " is missing", firstIndex >= 0);
        assertTrue(second + " is missing", secondIndex > firstIndex);
        String between = xml.substring(firstIndex + first.length(), secondIndex);
        assertTrue(first + " must be immediately before " + second,
                !between.contains("<item"));
    }

    private static String readMenu() throws IOException {
        Path path = Paths.get("src/main/res/menu/menu_chat.xml");
        if (!Files.exists(path))
            path = Paths.get("app/src/main/res/menu/menu_chat.xml");
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
