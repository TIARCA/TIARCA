package io.mrarm.irc.util.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class ThemeArchiveTest {

    @Test
    public void v2RoundTripPreservesSections() throws Exception {
        ThemeInfo theme = new ThemeInfo();
        theme.name = "Portable";
        theme.base = "default_dark";
        theme.colors.put(ThemeInfo.COLOR_PRIMARY, 0xFF112233);

        theme.ui = new ThemeInfo.UiSection();
        theme.ui.appBarCompactMode = "auto";

        theme.chat = new ThemeInfo.ChatSection();
        theme.chat.font = "monospace";
        theme.chat.fontSize = 17;

        theme.messageLayout = new ThemeInfo.MessageLayoutSection();
        theme.messageLayout.timeFormat = "[HH:mm]";
        theme.messageLayout.timeFixedWidth = true;
        theme.messageLayout.timeRight = true;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ThemeArchive.write(theme, null, null, out);

        byte[] archive = out.toByteArray();
        assertTrue(archive.length > 2);
        assertEquals('P', archive[0]);
        assertEquals('K', archive[1]);

        ThemeArchive.ImportedTheme imported =
                ThemeArchive.read(new ByteArrayInputStream(archive));
        assertEquals(Integer.valueOf(2), imported.theme.formatVersion);
        assertEquals("Portable", imported.theme.name);
        assertEquals("default_dark", imported.theme.base);
        assertEquals("auto", imported.theme.ui.appBarCompactMode);
        assertEquals("monospace", imported.theme.chat.font);
        assertEquals(Integer.valueOf(17), imported.theme.chat.fontSize);
        assertEquals("[HH:mm]", imported.theme.messageLayout.timeFormat);
        assertEquals(Boolean.TRUE, imported.theme.messageLayout.timeRight);
        assertNull(imported.fontData);
    }

    @Test
    public void legacyJsonStillImports() throws Exception {
        String legacy = "{\"base\":\"default_dark\",\"colors\":{},"
                + "\"name\":\"Legacy\",\"properties\":{},\"savedColors\":[]}";
        ThemeArchive.ImportedTheme imported = ThemeArchive.read(
                new ByteArrayInputStream(legacy.getBytes(StandardCharsets.UTF_8)));

        assertEquals("Legacy", imported.theme.name);
        assertEquals("default_dark", imported.theme.base);
        assertNull(imported.theme.formatVersion);
        assertNull(imported.theme.ui);
        assertNull(imported.theme.chat);
        assertNull(imported.theme.messageLayout);
    }

    @Test
    public void unknownFutureSectionsDoNotBreakV2Reader() throws Exception {
        String json = "{\"formatVersion\":3,\"name\":\"Future\","
                + "\"base\":\"default\",\"colors\":{},\"properties\":{},"
                + "\"savedColors\":[],\"avatar\":{\"size\":28,\"shape\":\"circle\"}}";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(ThemeArchive.THEME_JSON_ENTRY));
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        ThemeArchive.ImportedTheme imported = ThemeArchive.read(
                new ByteArrayInputStream(out.toByteArray()));
        assertEquals(Integer.valueOf(3), imported.theme.formatVersion);
        assertEquals("Future", imported.theme.name);
        assertEquals("default", imported.theme.base);
    }
}
