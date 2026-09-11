package io.mrarm.irc.util.theme;

import com.google.gson.Gson;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import io.mrarm.irc.config.SettingsHelper;

/**
 * irctheme v2 container.
 *
 * v1 themes were plain JSON files. v2 keeps the .irctheme extension but stores a ZIP archive
 * containing theme.json and optional visual assets. Unknown future entries are ignored, so new
 * sections/assets can be added without breaking older v2 readers.
 */
public final class ThemeArchive {

    public static final int FORMAT_VERSION = 2;
    public static final String THEME_JSON_ENTRY = "theme.json";
    public static final String ASSET_PREFIX = "assets/";

    private static final int MAX_JSON_BYTES = 1024 * 1024;
    private static final int MAX_ASSET_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ENTRIES = 64;

    private ThemeArchive() {
    }

    public static void write(ThemeInfo theme, InputStream fontStream, String fontEntryName,
                             OutputStream output) throws IOException {
        theme.formatVersion = FORMAT_VERSION;
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(THEME_JSON_ENTRY));
            byte[] json = SettingsHelper.getGson().toJson(theme)
                    .getBytes(StandardCharsets.UTF_8);
            zip.write(json);
            zip.closeEntry();

            if (fontStream != null && fontEntryName != null) {
                String safeName = sanitizeAssetEntry(fontEntryName);
                zip.putNextEntry(new ZipEntry(safeName));
                copyLimited(fontStream, zip, MAX_ASSET_BYTES);
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    public static ImportedTheme read(InputStream input) throws IOException {
        BufferedInputStream buffered = input instanceof BufferedInputStream
                ? (BufferedInputStream) input : new BufferedInputStream(input);
        buffered.mark(4);
        int first = buffered.read();
        int second = buffered.read();
        buffered.reset();

        if (first == 'P' && second == 'K')
            return readArchive(buffered);
        return readLegacyJson(buffered);
    }

    private static ImportedTheme readLegacyJson(InputStream input) throws IOException {
        byte[] json = readLimited(input, MAX_JSON_BYTES);
        ThemeInfo theme = parseTheme(json);
        // Missing formatVersion identifies the historical JSON-only format.
        return new ImportedTheme(theme, null, null);
    }

    private static ImportedTheme readArchive(InputStream input) throws IOException {
        byte[] themeJson = null;
        byte[] fontData = null;
        String fontEntry = null;
        int entries = 0;

        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES)
                    throw new IOException("Too many theme archive entries");
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }

                String name = entry.getName();
                if (THEME_JSON_ENTRY.equals(name)) {
                    themeJson = readLimited(zip, MAX_JSON_BYTES);
                } else if (name.startsWith(ASSET_PREFIX) && fontData == null) {
                    // v2 currently defines one optional font asset. Future assets are ignored.
                    fontData = readLimited(zip, MAX_ASSET_BYTES);
                    fontEntry = sanitizeAssetEntry(name);
                }
                zip.closeEntry();
            }
        }

        if (themeJson == null)
            throw new IOException("Missing theme.json");

        ThemeInfo theme = parseTheme(themeJson);
        if (theme.formatVersion == null)
            theme.formatVersion = FORMAT_VERSION;

        if (theme.chat == null || theme.chat.fontAsset == null) {
            fontData = null;
            fontEntry = null;
        } else if (fontEntry == null || !theme.chat.fontAsset.equals(fontEntry)) {
            // Never bind arbitrary archive data to a different path declared by JSON.
            fontData = null;
            fontEntry = null;
        }
        return new ImportedTheme(theme, fontData, fontEntry);
    }

    private static ThemeInfo parseTheme(byte[] json) throws IOException {
        try {
            Gson gson = SettingsHelper.getGson();
            ThemeInfo theme = gson.fromJson(new InputStreamReader(
                    new ByteArrayInputStream(json), StandardCharsets.UTF_8), ThemeInfo.class);
            if (theme == null)
                throw new IOException("Empty theme");
            if (theme.colors == null)
                theme.colors = new java.util.HashMap<>();
            if (theme.properties == null)
                theme.properties = new java.util.HashMap<>();
            if (theme.savedColors == null)
                theme.savedColors = new java.util.ArrayList<>();
            return theme;
        } catch (RuntimeException e) {
            throw new IOException("Invalid theme data", e);
        }
    }

    private static String sanitizeAssetEntry(String name) throws IOException {
        if (name == null || !name.startsWith(ASSET_PREFIX) || name.contains("..")
                || name.startsWith("/") || name.contains("\\"))
            throw new IOException("Invalid theme asset path");
        String leaf = name.substring(ASSET_PREFIX.length());
        if (leaf.isEmpty() || leaf.contains("/"))
            throw new IOException("Invalid theme asset path");
        return ASSET_PREFIX + leaf;
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copyLimited(input, out, maxBytes);
        return out.toByteArray();
    }

    private static void copyLimited(InputStream input, OutputStream output, int maxBytes)
            throws IOException {
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maxBytes)
                throw new IOException("Theme entry too large");
            output.write(buffer, 0, count);
        }
    }

    public static final class ImportedTheme {
        public final ThemeInfo theme;
        public final byte[] fontData;
        public final String fontEntryName;

        ImportedTheme(ThemeInfo theme, byte[] fontData, String fontEntryName) {
            this.theme = theme;
            this.fontData = fontData;
            this.fontEntryName = fontEntryName;
        }
    }
}
