package io.mrarm.irc;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class GuideNavigationStructureTest {
    private static String read(String full, String relative) throws Exception {
        Path path = Paths.get(full);
        if (!Files.exists(path)) path = Paths.get(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test public void guideIsUnderSettingsAndNotExported() throws Exception {
        String drawer = read("app/src/main/java/io/mrarm/irc/drawer/DrawerHelper.java",
                "src/main/java/io/mrarm/irc/drawer/DrawerHelper.java");
        assertTrue(drawer.indexOf("mAdapter.addMenuItem(mSettingsItem)") <
                drawer.indexOf("mAdapter.addMenuItem(guideItem)"));
        String manifest = read("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml");
        assertTrue(manifest.contains("android:name=\".GuideActivity\"\n            android:exported=\"false\""));
    }

    @Test public void guideReturnsDraftOnlyWithConfirmation() throws Exception {
        String main = read("app/src/main/java/io/mrarm/irc/MainActivity.java",
                "src/main/java/io/mrarm/irc/MainActivity.java");
        String send = read("app/src/main/java/io/mrarm/irc/chat/ChatFragmentSendMessageHelper.java",
                "src/main/java/io/mrarm/irc/chat/ChatFragmentSendMessageHelper.java");
        assertTrue(main.contains("helper.hasDraftText()"));
        assertTrue(main.contains("setPositiveButton(R.string.guide_replace_draft_confirm"));
        assertTrue(send.contains("public void insertGuideCommand(String command)"));
        assertFalse(send.contains("sendMessage(command)"));
    }
}
