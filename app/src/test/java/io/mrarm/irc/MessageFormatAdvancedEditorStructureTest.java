package io.mrarm.irc;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression tests for the advanced message-format editor UI. */
public class MessageFormatAdvancedEditorStructureTest {

    private static final String[] ADVANCED_PRESET_BUTTONS = {
            "message_format_normal_preset",
            "message_format_normal_mention_preset",
            "message_format_action_preset",
            "message_format_action_mention_preset",
            "message_format_notice_preset",
            "message_format_event_preset"
    };

    @Test
    public void wholeFormatPresetPopupButtonsStayHidden() throws IOException {
        String xml = readLayout();
        for (String id : ADVANCED_PRESET_BUTTONS) {
            String item = elementContaining(xml, "@+id/" + id);
            assertTrue(id + " must stay hidden so it cannot cover the example preview",
                    item.contains("android:visibility=\"gone\""));
        }
    }

    @Test
    public void timeFormatPresetButtonRemainsAvailable() throws IOException {
        String xml = readLayout();
        String item = elementContaining(xml, "@+id/date_format_preset");
        assertFalse("The timestamp-format chooser is unrelated and must remain available",
                item.contains("android:visibility=\"gone\""));
    }

    private static String elementContaining(String xml, String id) {
        int idPos = xml.indexOf(id);
        assertTrue(id + " is missing", idPos >= 0);
        int start = xml.lastIndexOf("<ImageButton", idPos);
        int end = xml.indexOf("/>", idPos);
        assertTrue(id + " ImageButton is malformed", start >= 0 && end > idPos);
        return xml.substring(start, end + 2);
    }

    private static String readLayout() throws IOException {
        Path path = Paths.get("src/main/res/layout/activity_message_format_settings.xml");
        if (!Files.exists(path))
            path = Paths.get("app/src/main/res/layout/activity_message_format_settings.xml");
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
