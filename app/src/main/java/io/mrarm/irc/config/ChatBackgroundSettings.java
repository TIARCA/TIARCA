package io.mrarm.irc.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.mrarm.irc.util.DefaultPreferences;

/** Preferences and private-file storage for the chat background. */
public final class ChatBackgroundSettings {

    public static final String PREF_TYPE = "chat_background_type";
    public static final String PREF_COLOR = "chat_background_color";
    public static final String PREF_SCALE = "chat_background_scale";
    public static final String PREF_ZOOM = "chat_background_zoom";
    public static final String PREF_FOCUS_X = "chat_background_focus_x";
    public static final String PREF_FOCUS_Y = "chat_background_focus_y";
    public static final String PREF_OPACITY = "chat_background_opacity";
    /** Internal change signal; it is intentionally not serialized into portable presets. */
    public static final String PREF_IMAGE_REVISION = "chat_background_image_revision";

    public static final String TYPE_COLOR = "color";
    public static final String TYPE_IMAGE = "image";

    /** Legacy scale modes remain readable for old presets, but are no longer exposed in the UI. */
    public static final String SCALE_FILL = "fill";
    public static final String SCALE_FIT = "fit";
    public static final String SCALE_STRETCH = "stretch";
    public static final String SCALE_MATRIX = "matrix";

    public static final float DEFAULT_ZOOM = 1f;
    public static final float DEFAULT_FOCUS = 0.5f;
    public static final int DEFAULT_OPACITY = 100;
    public static final float MAX_ZOOM = 6f;

    private static final String IMAGE_DIR = "interface";
    private static final String IMAGE_FILE = "chat-background";
    private static final String CANDIDATE_FILE = "chat-background-candidate";
    private static final int MAX_IMAGE_BYTES = 16 * 1024 * 1024;

    private ChatBackgroundSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return DefaultPreferences.get(context.getApplicationContext());
    }

    public static String getType(Context context) {
        String value = prefs(context).getString(PREF_TYPE, TYPE_COLOR);
        return TYPE_IMAGE.equals(value) ? TYPE_IMAGE : TYPE_COLOR;
    }

    public static String getScale(Context context) {
        String value = prefs(context).getString(PREF_SCALE, SCALE_FILL);
        if (SCALE_FIT.equals(value) || SCALE_STRETCH.equals(value) || SCALE_MATRIX.equals(value))
            return value;
        return SCALE_FILL;
    }

    public static float getZoom(Context context) {
        return clamp(prefs(context).getFloat(PREF_ZOOM, DEFAULT_ZOOM), DEFAULT_ZOOM, MAX_ZOOM);
    }

    public static float getFocusX(Context context) {
        return clamp01(prefs(context).getFloat(PREF_FOCUS_X, DEFAULT_FOCUS));
    }

    public static float getFocusY(Context context) {
        return clamp01(prefs(context).getFloat(PREF_FOCUS_Y, DEFAULT_FOCUS));
    }

    public static int getOpacity(Context context) {
        return Math.max(0, Math.min(100,
                prefs(context).getInt(PREF_OPACITY, DEFAULT_OPACITY)));
    }

    public static boolean hasCustomColor(Context context) {
        return prefs(context).contains(PREF_COLOR);
    }

    public static int getCustomColor(Context context, int fallback) {
        return prefs(context).getInt(PREF_COLOR, fallback);
    }

    public static File getImageFile(Context context) {
        return new File(new File(context.getApplicationContext().getFilesDir(), IMAGE_DIR),
                IMAGE_FILE);
    }

    public static File getCandidateImageFile(Context context) {
        return new File(new File(context.getApplicationContext().getFilesDir(), IMAGE_DIR),
                CANDIDATE_FILE);
    }

    public static boolean hasImage(Context context) {
        File file = getImageFile(context);
        return file.isFile() && file.length() > 0;
    }

    public static void useColor(Context context, int color) {
        prefs(context).edit()
                .putString(PREF_TYPE, TYPE_COLOR)
                .putInt(PREF_COLOR, color)
                .apply();
    }

    public static void useThemeColor(Context context) {
        prefs(context).edit()
                .putString(PREF_TYPE, TYPE_COLOR)
                .remove(PREF_COLOR)
                .apply();
    }

    public static void setScale(Context context, String scale) {
        if (!SCALE_FIT.equals(scale) && !SCALE_STRETCH.equals(scale)
                && !SCALE_MATRIX.equals(scale))
            scale = SCALE_FILL;
        prefs(context).edit().putString(PREF_SCALE, scale).apply();
    }

    public static void setTransform(Context context, float zoom, float focusX, float focusY,
                                    int opacity) {
        prefs(context).edit()
                .putString(PREF_SCALE, SCALE_MATRIX)
                .putFloat(PREF_ZOOM, clamp(zoom, DEFAULT_ZOOM, MAX_ZOOM))
                .putFloat(PREF_FOCUS_X, clamp01(focusX))
                .putFloat(PREF_FOCUS_Y, clamp01(focusY))
                .putInt(PREF_OPACITY, Math.max(0, Math.min(100, opacity)))
                .apply();
    }

    /** Copies a picked/imported image into TIARCA's active private storage. */
    public static void storeImage(Context context, InputStream input) throws IOException {
        replaceFileFromStream(getImageFile(context), input);
        signalActiveImageChanged(context);
    }

    /** Stores a newly picked image without changing the currently active background. */
    public static void storeCandidateImage(Context context, InputStream input) throws IOException {
        replaceFileFromStream(getCandidateImageFile(context), input);
    }

    /** Copies the active image to the editor candidate slot. */
    public static void prepareCandidateFromActive(Context context) throws IOException {
        File source = getImageFile(context);
        if (!source.isFile())
            throw new IOException("Missing active background image");
        try (FileInputStream in = new FileInputStream(source)) {
            replaceFileFromStream(getCandidateImageFile(context), in);
        }
    }

    /** Commits the editor candidate atomically and activates the WYSIWYG transform. */
    public static void commitCandidate(Context context, float zoom, float focusX, float focusY,
                                       int opacity) throws IOException {
        File candidate = getCandidateImageFile(context);
        if (!candidate.isFile())
            throw new IOException("Missing background candidate");
        File destination = getImageFile(context);
        ensureParent(destination);
        if (destination.exists() && !destination.delete())
            throw new IOException("Unable to replace background image");
        if (!candidate.renameTo(destination)) {
            try (FileInputStream in = new FileInputStream(candidate)) {
                replaceFileFromStream(destination, in);
            }
            candidate.delete();
        }
        SharedPreferences preferences = prefs(context);
        long revision = preferences.getLong(PREF_IMAGE_REVISION, 0L) + 1L;
        preferences.edit()
                .putString(PREF_TYPE, TYPE_IMAGE)
                .putString(PREF_SCALE, SCALE_MATRIX)
                .putFloat(PREF_ZOOM, clamp(zoom, DEFAULT_ZOOM, MAX_ZOOM))
                .putFloat(PREF_FOCUS_X, clamp01(focusX))
                .putFloat(PREF_FOCUS_Y, clamp01(focusY))
                .putInt(PREF_OPACITY, Math.max(0, Math.min(100, opacity)))
                .putLong(PREF_IMAGE_REVISION, revision)
                .apply();
    }

    public static void discardCandidate(Context context) {
        File file = getCandidateImageFile(context);
        if (file.exists())
            file.delete();
    }

    /** Restores a preset-owned background asset into the active private background file. */
    public static void restoreImage(Context context, File source) throws IOException {
        if (source == null || !source.isFile())
            throw new IOException("Missing background asset");
        try (FileInputStream in = new FileInputStream(source)) {
            storeImage(context, in);
        }
    }

    public static void resetToThemeDefault(Context context) {
        prefs(context).edit()
                .putString(PREF_TYPE, TYPE_COLOR)
                .remove(PREF_COLOR)
                .putString(PREF_SCALE, SCALE_FILL)
                .remove(PREF_ZOOM)
                .remove(PREF_FOCUS_X)
                .remove(PREF_FOCUS_Y)
                .remove(PREF_OPACITY)
                .apply();
        File image = getImageFile(context);
        if (image.exists())
            image.delete();
        discardCandidate(context);
    }

    /** Decode only as much resolution as a screen-sized background needs. */
    public static Bitmap decodeSampledImage(Context context, int targetWidth, int targetHeight) {
        return decodeSampledFile(getImageFile(context), targetWidth, targetHeight);
    }

    public static Bitmap decodeSampledCandidate(Context context, int targetWidth, int targetHeight) {
        return decodeSampledFile(getCandidateImageFile(context), targetWidth, targetHeight);
    }

    public static Bitmap decodeSampledFile(File image, int targetWidth, int targetHeight) {
        if (image == null || !image.isFile())
            return null;

        int safeWidth = Math.max(1, targetWidth);
        int safeHeight = Math.max(1, targetHeight);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            return null;

        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= safeWidth
                && bounds.outHeight / (sample * 2) >= safeHeight)
            sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(image.getAbsolutePath(), options);
    }

    private static void signalActiveImageChanged(Context context) {
        SharedPreferences preferences = prefs(context);
        long revision = preferences.getLong(PREF_IMAGE_REVISION, 0L) + 1L;
        preferences.edit()
                .putString(PREF_TYPE, TYPE_IMAGE)
                .putString(PREF_SCALE, getScale(context))
                .putLong(PREF_IMAGE_REVISION, revision)
                .apply();
    }

    private static void replaceFileFromStream(File destination, InputStream input)
            throws IOException {
        ensureParent(destination);
        File parent = destination.getParentFile();
        File temp = new File(parent, destination.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            copyLimited(input, out, MAX_IMAGE_BYTES);
        } catch (IOException e) {
            temp.delete();
            throw e;
        }

        if (destination.exists() && !destination.delete()) {
            temp.delete();
            throw new IOException("Unable to replace background image");
        }
        if (!temp.renameTo(destination)) {
            try (FileInputStream in = new FileInputStream(temp);
                 FileOutputStream out = new FileOutputStream(destination)) {
                copyLimited(in, out, MAX_IMAGE_BYTES);
            } finally {
                temp.delete();
            }
        }
    }

    private static void ensureParent(File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("Unable to create background directory");
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void copyLimited(InputStream input, OutputStream output, int maxBytes)
            throws IOException {
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maxBytes)
                throw new IOException("Background image too large");
            output.write(buffer, 0, count);
        }
    }
}
