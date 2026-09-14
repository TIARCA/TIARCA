package io.mrarm.irc.config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;

import io.mrarm.irc.util.DefaultPreferences;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ChatBackgroundSettingsTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DefaultPreferences.get(context).edit().clear().commit();
        ChatBackgroundSettings.getImageFile(context).delete();
        ChatBackgroundSettings.discardCandidate(context);
    }

    @Test
    public void candidateDoesNotReplaceActiveImageUntilApply() throws Exception {
        byte[] oldImage = "old-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] newImage = "new-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ChatBackgroundSettings.storeImage(context, new ByteArrayInputStream(oldImage));
        ChatBackgroundSettings.setTransform(context, 1.5f, 0.3f, 0.7f, 60);

        ChatBackgroundSettings.storeCandidateImage(context, new ByteArrayInputStream(newImage));

        assertArrayEquals(oldImage, readActive());
        assertTrue(ChatBackgroundSettings.getCandidateImageFile(context).isFile());
        assertEquals(1.5f, ChatBackgroundSettings.getZoom(context), 0.0001f);
        assertEquals(60, ChatBackgroundSettings.getOpacity(context));

        ChatBackgroundSettings.discardCandidate(context);
        assertArrayEquals(oldImage, readActive());
        assertFalse(ChatBackgroundSettings.getCandidateImageFile(context).exists());
    }

    @Test
    public void commitCandidateAtomicallyActivatesImageAndTransform() throws Exception {
        byte[] newImage = "candidate".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ChatBackgroundSettings.storeCandidateImage(context, new ByteArrayInputStream(newImage));

        ChatBackgroundSettings.commitCandidate(context, 2.5f, 0.25f, 0.75f, 42);

        assertArrayEquals(newImage, readActive());
        assertFalse(ChatBackgroundSettings.getCandidateImageFile(context).exists());
        assertEquals(ChatBackgroundSettings.TYPE_IMAGE, ChatBackgroundSettings.getType(context));
        assertEquals(ChatBackgroundSettings.SCALE_MATRIX, ChatBackgroundSettings.getScale(context));
        assertEquals(2.5f, ChatBackgroundSettings.getZoom(context), 0.0001f);
        assertEquals(0.25f, ChatBackgroundSettings.getFocusX(context), 0.0001f);
        assertEquals(0.75f, ChatBackgroundSettings.getFocusY(context), 0.0001f);
        assertEquals(42, ChatBackgroundSettings.getOpacity(context));
    }

    private byte[] readActive() throws Exception {
        try (InputStream in = new FileInputStream(ChatBackgroundSettings.getImageFile(context))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[128];
            int count;
            while ((count = in.read(buffer)) != -1)
                out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }
}
