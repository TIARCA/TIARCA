package io.mrarm.irc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import io.mrarm.irc.util.theme.ThemeArchive;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PresetImportActivityTest {

    @Test
    public void customPresetMimeTypeIsAcceptedForContentUri() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("content://provider/document/opaque-id"),
                ThemeArchive.MIME_TYPE);

        assertTrue(PresetImportActivity.isSupportedPresetIntent(intent, "shared-file"));
    }

    @Test
    public void ircpresetExtensionIsAcceptedWithGenericMimeType() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("content://provider/document/MyPreset.ircpreset"),
                "application/octet-stream");

        assertTrue(PresetImportActivity.isSupportedPresetIntent(intent, "MyPreset.ircpreset"));
        assertTrue(PresetImportActivity.hasPresetExtension("PRESET.IRCPRESET"));
    }

    @Test
    public void unrelatedZipIsRejected() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("content://provider/document/archive.zip"),
                "application/zip");

        assertFalse(PresetImportActivity.isSupportedPresetIntent(intent, "archive.zip"));
    }

    @Test
    public void nonViewAndWebIntentsAreRejected() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setDataAndType(Uri.parse("content://provider/document/preset.ircpreset"),
                ThemeArchive.MIME_TYPE);
        assertFalse(PresetImportActivity.isSupportedPresetIntent(send, "preset.ircpreset"));

        Intent web = new Intent(Intent.ACTION_VIEW);
        web.setDataAndType(Uri.parse("https://example.com/preset.ircpreset"),
                ThemeArchive.MIME_TYPE);
        assertFalse(PresetImportActivity.isSupportedPresetIntent(web, "preset.ircpreset"));
    }

    @Test
    public void manifestRegistersPresetMimeButNotOrdinaryZip() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent preset = new Intent(Intent.ACTION_VIEW);
        preset.setDataAndType(Uri.parse("content://provider/document/preset.ircpreset"),
                ThemeArchive.MIME_TYPE);
        preset.addCategory(Intent.CATEGORY_BROWSABLE);

        List<ResolveInfo> presetHandlers = context.getPackageManager()
                .queryIntentActivities(preset, 0);
        assertTrue(containsPresetImportActivity(presetHandlers));

        Intent zip = new Intent(Intent.ACTION_VIEW);
        zip.setDataAndType(Uri.parse("content://provider/document/archive.zip"),
                "application/zip");
        zip.addCategory(Intent.CATEGORY_BROWSABLE);
        assertFalse(containsPresetImportActivity(
                context.getPackageManager().queryIntentActivities(zip, 0)));
    }

    private static boolean containsPresetImportActivity(List<ResolveInfo> handlers) {
        for (ResolveInfo info : handlers) {
            if (info.activityInfo != null
                    && PresetImportActivity.class.getName().equals(info.activityInfo.name))
                return true;
        }
        return false;
    }
}
