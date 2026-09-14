package io.mrarm.irc.util.theme;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class ThemeArchiveTest {

    @Test
    public void v3RoundTripPreservesSectionsAndMultipleAssets() throws Exception {
        ThemeInfo theme = new ThemeInfo();
        theme.name = "Portable";
        theme.base = "default_dark";
        theme.colors.put(ThemeInfo.COLOR_PRIMARY, 0xFF112233);

        theme.ui = new ThemeInfo.UiSection();
        theme.ui.appearancePreset = AppearancePreset.COLOR_BLIND.getId();
        theme.ui.appBarCompactMode = "auto";

        theme.chat = new ThemeInfo.ChatSection();
        theme.chat.font = "monospace";
        theme.chat.fontSize = 17;
        theme.chat.globalFontEnabled = true;
        theme.chat.textAutocorrectEnabled = false;
        theme.chat.sendBoxAlwaysMultiline = true;
        theme.chat.monochromeBots = true;
        theme.chat.fontAsset = "assets/font.ttf";
        theme.assets.put(ThemeInfo.ASSET_FONT, "assets/font.ttf");
        theme.assets.put("background", "assets/background.webp");

        theme.messageLayout = new ThemeInfo.MessageLayoutSection();
        theme.messageLayout.timeFormat = "[HH:mm]";
        theme.messageLayout.timeFixedWidth = true;
        theme.messageLayout.timeRight = true;

        byte[] font = "font-data".getBytes(StandardCharsets.UTF_8);
        byte[] background = "image-data".getBytes(StandardCharsets.UTF_8);
        Map<String, InputStream> assets = new LinkedHashMap<>();
        assets.put("assets/background.webp", new ByteArrayInputStream(background));
        assets.put("assets/font.ttf", new ByteArrayInputStream(font));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ThemeArchive.write(theme, assets, out);

        byte[] archive = out.toByteArray();
        assertTrue(archive.length > 2);
        assertEquals('P', archive[0]);
        assertEquals('K', archive[1]);

        // Font is intentionally emitted before other assets so a v2 reader still finds it.
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            assertEquals(ThemeArchive.THEME_JSON_ENTRY, zip.getNextEntry().getName());
            zip.closeEntry();
            assertEquals("assets/font.ttf", zip.getNextEntry().getName());
        }

        ThemeArchive.ImportedTheme imported =
                ThemeArchive.read(new ByteArrayInputStream(archive));
        assertEquals(Integer.valueOf(3), imported.theme.formatVersion);
        assertEquals("Portable", imported.theme.name);
        assertEquals("default_dark", imported.theme.base);
        assertEquals(AppearancePreset.COLOR_BLIND.getId(),
                imported.theme.ui.appearancePreset);
        assertEquals("auto", imported.theme.ui.appBarCompactMode);
        assertEquals("monospace", imported.theme.chat.font);
        assertEquals(Integer.valueOf(17), imported.theme.chat.fontSize);
        assertEquals(Boolean.TRUE, imported.theme.chat.globalFontEnabled);
        assertEquals(Boolean.FALSE, imported.theme.chat.textAutocorrectEnabled);
        assertEquals(Boolean.TRUE, imported.theme.chat.sendBoxAlwaysMultiline);
        assertEquals(Boolean.TRUE, imported.theme.chat.monochromeBots);
        assertEquals("[HH:mm]", imported.theme.messageLayout.timeFormat);
        assertEquals(Boolean.TRUE, imported.theme.messageLayout.timeRight);
        assertEquals("assets/font.ttf", imported.theme.assets.get(ThemeInfo.ASSET_FONT));
        assertEquals("assets/background.webp", imported.theme.assets.get("background"));
        assertArrayEquals(font, imported.assets.get("assets/font.ttf"));
        assertArrayEquals(background, imported.assets.get("assets/background.webp"));
        assertArrayEquals(font, imported.fontData);
        assertEquals("assets/font.ttf", imported.fontEntryName);
    }

    @Test
    public void v2ArchiveStillImportsLegacyFontWithoutAssetManifest() throws Exception {
        String json = "{\"formatVersion\":2,\"name\":\"V2\","
                + "\"base\":\"default_dark\",\"colors\":{},\"properties\":{},"
                + "\"savedColors\":[],\"chat\":{\"font\":\"custom:test.ttf\","
                + "\"fontAsset\":\"assets/font.ttf\"}}";
        byte[] font = "legacy-font".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(ThemeArchive.THEME_JSON_ENTRY));
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("assets/font.ttf"));
            zip.write(font);
            zip.closeEntry();
        }

        ThemeArchive.ImportedTheme imported = ThemeArchive.read(
                new ByteArrayInputStream(out.toByteArray()));
        assertEquals(Integer.valueOf(2), imported.theme.formatVersion);
        assertArrayEquals(font, imported.assets.get("assets/font.ttf"));
        assertArrayEquals(font, imported.fontData);
    }

    @Test
    public void undeclaredAssetIsNotExposedToImporter() throws Exception {
        String json = "{\"formatVersion\":3,\"name\":\"Portable\","
                + "\"base\":\"default\",\"colors\":{},\"properties\":{},"
                + "\"savedColors\":[],\"assets\":{}}";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(ThemeArchive.THEME_JSON_ENTRY));
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("assets/not-declared.bin"));
            zip.write(new byte[] { 1, 2, 3 });
            zip.closeEntry();
        }

        ThemeArchive.ImportedTheme imported = ThemeArchive.read(
                new ByteArrayInputStream(out.toByteArray()));
        assertTrue(imported.assets.isEmpty());
    }

    @Test
    public void legacyJsonStillParsesBeforeMigration() throws Exception {
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
        assertTrue(imported.assets.isEmpty());
    }

    @Test
    public void unknownFutureSectionsDoNotBreakCurrentReader() throws Exception {
        String json = "{\"formatVersion\":4,\"name\":\"Future\","
                + "\"base\":\"default\",\"colors\":{},\"properties\":{},"
                + "\"savedColors\":[],\"assets\":{},"
                + "\"avatar\":{\"size\":28,\"shape\":\"circle\"}}";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(ThemeArchive.THEME_JSON_ENTRY));
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        ThemeArchive.ImportedTheme imported = ThemeArchive.read(
                new ByteArrayInputStream(out.toByteArray()));
        assertEquals(Integer.valueOf(4), imported.theme.formatVersion);
        assertEquals("Future", imported.theme.name);
        assertEquals("default", imported.theme.base);
        assertFalse(imported.theme.assets.containsKey("avatar"));
    }
}
