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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import io.mrarm.irc.config.SettingsHelper;

/** Portable interface-preset container. */
public final class ThemeArchive {

    /** v3 adds a generic named-asset manifest and deterministic migration support. */
    public static final int FORMAT_VERSION = 3;
    public static final String FILE_EXTENSION = ".ircpreset";
    public static final String LEGACY_FILE_EXTENSION = ".irctheme";
    public static final String MIME_TYPE = "application/x-tiarca-preset";
    public static final String THEME_JSON_ENTRY = "theme.json";
    public static final String ASSET_PREFIX = "assets/";

    private static final int MAX_JSON_BYTES = 1024 * 1024;
    private static final int MAX_ASSET_BYTES = 16 * 1024 * 1024;
    private static final int MAX_TOTAL_ASSET_BYTES = 32 * 1024 * 1024;
    private static final int MAX_ENTRIES = 64;

    private ThemeArchive() {
    }

    /**
     * Writes a v3 preset. Asset streams are keyed by their archive path (for example
     * {@code assets/font.ttf}); only paths declared by the preset manifest are emitted.
     */
    public static void write(ThemeInfo theme, Map<String, InputStream> assetStreams,
                             OutputStream output) throws IOException {
        if (theme.assets == null)
            theme.assets = new HashMap<>();
        if (theme.chat != null && theme.chat.fontAsset != null) {
            String fontPath = sanitizeAssetEntry(theme.chat.fontAsset);
            theme.chat.fontAsset = fontPath;
            theme.assets.put(ThemeInfo.ASSET_FONT, fontPath);
        }
        theme.formatVersion = FORMAT_VERSION;

        List<String> assetPaths = getDeclaredAssetPaths(theme);
        if (assetPaths.size() + 1 > MAX_ENTRIES)
            throw new IOException("Too many theme archive entries");

        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(THEME_JSON_ENTRY));
            byte[] json = SettingsHelper.getGson().toJson(theme)
                    .getBytes(StandardCharsets.UTF_8);
            if (json.length > MAX_JSON_BYTES)
                throw new IOException("Theme entry too large");
            zip.write(json);
            zip.closeEntry();

            int totalAssetBytes = 0;
            if (assetStreams != null) {
                for (String assetPath : assetPaths) {
                    InputStream stream = assetStreams.get(assetPath);
                    if (stream == null)
                        continue;
                    zip.putNextEntry(new ZipEntry(assetPath));
                    int written = copyLimited(stream, zip, MAX_ASSET_BYTES);
                    totalAssetBytes += written;
                    if (totalAssetBytes > MAX_TOTAL_ASSET_BYTES)
                        throw new IOException("Theme assets too large");
                    zip.closeEntry();
                }
            }
            zip.finish();
        }
    }

    /**
     * Compatibility overload for callers that still expose a single font stream. New code should
     * use the generic asset-map overload.
     */
    public static void write(ThemeInfo theme, InputStream fontStream, String fontEntryName,
                             OutputStream output) throws IOException {
        Map<String, InputStream> assets = new LinkedHashMap<>();
        if (fontStream != null && fontEntryName != null) {
            String safeName = sanitizeAssetEntry(fontEntryName);
            if (theme.assets == null)
                theme.assets = new HashMap<>();
            theme.assets.put(ThemeInfo.ASSET_FONT, safeName);
            if (theme.chat != null)
                theme.chat.fontAsset = safeName;
            assets.put(safeName, fontStream);
        }
        write(theme, assets, output);
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
        // Missing formatVersion identifies the historical JSON-only format. Migration is performed
        // by ThemePresetMigrator when the preset is imported/applied, where Android context is
        // available to construct the historical message-format defaults deterministically.
        return new ImportedTheme(theme, Collections.emptyMap());
    }

    private static ImportedTheme readArchive(InputStream input) throws IOException {
        byte[] themeJson = null;
        Map<String, byte[]> archiveAssets = new LinkedHashMap<>();
        Set<String> seenEntries = new HashSet<>();
        int entries = 0;
        int totalAssetBytes = 0;

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
                if (!seenEntries.add(name))
                    throw new IOException("Duplicate theme archive entry");
                if (THEME_JSON_ENTRY.equals(name)) {
                    themeJson = readLimited(zip, MAX_JSON_BYTES);
                } else if (name.startsWith(ASSET_PREFIX)) {
                    String safeName = sanitizeAssetEntry(name);
                    byte[] data = readLimited(zip, MAX_ASSET_BYTES);
                    totalAssetBytes += data.length;
                    if (totalAssetBytes > MAX_TOTAL_ASSET_BYTES)
                        throw new IOException("Theme assets too large");
                    archiveAssets.put(safeName, data);
                }
                zip.closeEntry();
            }
        }

        if (themeJson == null)
            throw new IOException("Missing theme.json");

        ThemeInfo theme = parseTheme(themeJson);
        if (theme.formatVersion == null)
            theme.formatVersion = 2; // historical ZIP container

        Map<String, byte[]> declaredAssets = new LinkedHashMap<>();
        for (String path : getDeclaredAssetPaths(theme)) {
            byte[] data = archiveAssets.get(path);
            if (data != null)
                declaredAssets.put(path, data);
        }
        return new ImportedTheme(theme, declaredAssets);
    }

    private static ThemeInfo parseTheme(byte[] json) throws IOException {
        try {
            Gson gson = SettingsHelper.getGson();
            ThemeInfo theme = gson.fromJson(new InputStreamReader(
                    new ByteArrayInputStream(json), StandardCharsets.UTF_8), ThemeInfo.class);
            if (theme == null)
                throw new IOException("Empty theme");
            if (theme.colors == null)
                theme.colors = new HashMap<>();
            if (theme.properties == null)
                theme.properties = new HashMap<>();
            if (theme.savedColors == null)
                theme.savedColors = new ArrayList<>();
            if (theme.assets == null)
                theme.assets = new HashMap<>();
            return theme;
        } catch (RuntimeException e) {
            throw new IOException("Invalid theme data", e);
        }
    }

    /** Returns declared asset paths with the legacy font first for old-reader compatibility. */
    static List<String> getDeclaredAssetPaths(ThemeInfo theme) throws IOException {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        if (theme.chat != null && theme.chat.fontAsset != null)
            paths.add(sanitizeAssetEntry(theme.chat.fontAsset));

        if (theme.assets != null) {
            List<String> remaining = new ArrayList<>();
            for (String path : theme.assets.values()) {
                if (path != null)
                    remaining.add(sanitizeAssetEntry(path));
            }
            Collections.sort(remaining);
            paths.addAll(remaining);
        }
        return new ArrayList<>(paths);
    }

    static String sanitizeAssetEntry(String name) throws IOException {
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

    private static int copyLimited(InputStream input, OutputStream output, int maxBytes)
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
        return total;
    }

    public static final class ImportedTheme {
        public final ThemeInfo theme;
        /** Immutable map of declared archive path -> asset bytes. */
        public final Map<String, byte[]> assets;

        /** @deprecated use {@link #assets}. Kept for source compatibility during the v2 window. */
        @Deprecated
        public final byte[] fontData;
        /** @deprecated use {@link #assets}. Kept for source compatibility during the v2 window. */
        @Deprecated
        public final String fontEntryName;

        ImportedTheme(ThemeInfo theme, Map<String, byte[]> assets) {
            this.theme = theme;
            this.assets = Collections.unmodifiableMap(new LinkedHashMap<>(assets));
            String fontPath = theme.chat == null ? null : theme.chat.fontAsset;
            this.fontEntryName = fontPath != null && assets.containsKey(fontPath)
                    ? fontPath : null;
            this.fontData = this.fontEntryName == null ? null : assets.get(this.fontEntryName);
        }
    }
}
