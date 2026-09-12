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

    @Test
    public void pvtToolbarActionsInExactOrder() throws IOException {
        String xml = readMenu();
        assertImmediatelyOrdered(xml, "@+id/action_send_media", "@+id/action_callerid_accept_toggle");
        assertImmediatelyOrdered(xml, "@+id/action_callerid_accept_toggle", "@+id/action_direct_ignore");
        assertImmediatelyOrdered(xml, "@+id/action_direct_ignore", "@+id/action_close_private_conversation");
    }

    @Test
    public void closePrivateConversationIsToolbarActionHiddenByDefault() throws IOException {
        String xml = readMenu();
        int start = xml.indexOf("@+id/action_close_private_conversation");
        assertTrue("Close private conversation action is missing", start >= 0);
        int end = xml.indexOf("/>", start);
        assertTrue("Close private conversation item is malformed", end > start);
        String item = xml.substring(start, end);
        assertTrue("Close private conversation must be hidden until PVT is active",
                item.contains("android:visible=\"false\""));
        assertTrue("Close private conversation must be a toolbar action",
                item.contains("app:showAsAction=\"always\""));
    }

    private static void assertImmediatelyOrdered(String xml, String first, String second) {
        int firstId = xml.indexOf(first);
        int secondId = xml.indexOf(second);
        assertTrue(first + " is missing", firstId >= 0);
        assertTrue(second + " is missing", secondId > firstId);

        int firstEnd = xml.indexOf("/>", firstId);
        int secondStart = xml.lastIndexOf("<item", secondId);
        assertTrue(first + " item is malformed", firstEnd > firstId);
        assertTrue(second + " item is malformed", secondStart > firstEnd);

        String betweenItems = xml.substring(firstEnd + 2, secondStart);
        assertTrue(first + " must be immediately before " + second,
                !betweenItems.contains("<item"));
    }

    private static String readMenu() throws IOException {
        Path path = Paths.get("src/main/res/menu/menu_chat.xml");
        if (!Files.exists(path))
            path = Paths.get("app/src/main/res/menu/menu_chat.xml");
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
