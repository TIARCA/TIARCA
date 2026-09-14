package io.mrarm.irc;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import io.mrarm.irc.util.theme.ThemeArchive;
import io.mrarm.irc.util.theme.ThemeInfo;
import io.mrarm.irc.util.theme.ThemeManager;
import io.mrarm.irc.util.theme.UserPresetStore;

/** Handles opening portable TIARCA preset files from Android file/share providers. */
public class PresetImportActivity extends ThemedActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handlePresetIntent(getIntent());
    }

    private void handlePresetIntent(Intent intent) {
        Uri uri = intent == null ? null : intent.getData();
        String displayName = getDisplayName(uri);
        if (!isSupportedPresetIntent(intent, displayName)) {
            showImportError();
            return;
        }

        String label = displayName;
        if (label == null || label.trim().isEmpty())
            label = ThemeArchive.FILE_EXTENSION;
        final String importedDisplayName = displayName;
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_import_preset)
                .setMessage(getString(R.string.preset_import_confirm, label))
                .setPositiveButton(R.string.action_import_preset,
                        (dialog, which) -> importPreset(uri, importedDisplayName))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void importPreset(Uri uri, String displayName) {
        ThemeManager manager = ThemeManager.getInstance(this);
        Set<UUID> before = UserPresetStore.snapshotThemeIds(manager);
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null)
                throw new IOException("Unable to open preset");
            manager.importTheme(input);
            ThemeInfo imported = UserPresetStore.findImportedTheme(manager, before);
            if (imported != null) {
                if (imported.name == null || imported.name.trim().isEmpty()) {
                    String fallback = UserPresetStore.nameFromFile(displayName);
                    if (fallback != null) {
                        imported.name = fallback;
                        manager.saveTheme(imported);
                    }
                }
                UserPresetStore.mark(this, imported);
            }
            Toast.makeText(this, R.string.preset_import_success, Toast.LENGTH_SHORT).show();
            openMainActivity();
        } catch (IOException | RuntimeException e) {
            // Do not log the external Uri or exception text: providers may include user paths.
            Log.w("PresetImport", "Failed to import external preset");
            showImportError();
        }
    }

    private void openMainActivity() {
        Intent main = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(main);
        finish();
    }

    private void showImportError() {
        if (isFinishing())
            return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_import_preset)
                .setMessage(R.string.preset_import_error)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private String getDisplayName(Uri uri) {
        if (uri == null)
            return null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri,
                    new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        String name = cursor.getString(index);
                        if (name != null && !name.trim().isEmpty())
                            return name;
                    }
                }
            } catch (RuntimeException ignored) {
                // Fall back to the Uri path below.
            }
        }
        return uri.getLastPathSegment();
    }

    static boolean isSupportedPresetIntent(Intent intent, String displayName) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())
                || intent.getData() == null)
            return false;
        Uri uri = intent.getData();
        String scheme = uri.getScheme();
        if (scheme != null && !"content".equalsIgnoreCase(scheme)
                && !"file".equalsIgnoreCase(scheme))
            return false;
        if (ThemeArchive.MIME_TYPE.equalsIgnoreCase(intent.getType()))
            return true;
        return hasPresetExtension(displayName) || hasPresetExtension(uri.getLastPathSegment())
                || hasPresetExtension(uri.getPath());
    }

    static boolean hasPresetExtension(String value) {
        return value != null && value.toLowerCase(Locale.ROOT)
                .endsWith(ThemeArchive.FILE_EXTENSION);
    }
}
