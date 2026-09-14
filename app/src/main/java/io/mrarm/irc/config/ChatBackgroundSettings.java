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

import io.mrarm.irc.util.DefaultPreferences;

/** Preferences and private-file storage for the chat background. */
public final class ChatBackgroundSettings {

    public static final String PREF_TYPE = "chat_background_type";
    public static final String PREF_COLOR = "chat_background_color";
    public static final String PREF_SCALE = "chat_background_scale";

    public static final String TYPE_COLOR = "color";
    public static final String TYPE_IMAGE = "image";

    public static final String SCALE_FILL = "fill";
    public static final String SCALE_FIT = "fit";
    public static final String SCALE_STRETCH = "stretch";

    private static final String IMAGE_DIR = "interface";
    private static final String IMAGE_FILE = "chat-background";
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
        if (SCALE_FIT.equals(value) || SCALE_STRETCH.equals(value))
            return value;
        return SCALE_FILL;
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
        if (!SCALE_FIT.equals(scale) && !SCALE_STRETCH.equals(scale))
            scale = SCALE_FILL;
        prefs(context).edit().putString(PREF_SCALE, scale).apply();
    }

    /** Copies a picked image into TIARCA's private storage, independent of the source Uri. */
    public static void storeImage(Context context, InputStream input) throws IOException {
        File destination = getImageFile(context);
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("Unable to create background directory");

        File temp = new File(parent, IMAGE_FILE + ".tmp");
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
        prefs(context).edit()
                .putString(PREF_TYPE, TYPE_IMAGE)
                .putString(PREF_SCALE, getScale(context))
                .apply();
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
                .apply();
        File image = getImageFile(context);
        if (image.exists())
            image.delete();
    }

    /** Decode only as much resolution as a screen-sized background needs. */
    public static Bitmap decodeSampledImage(Context context, int targetWidth, int targetHeight) {
        File image = getImageFile(context);
        if (!image.isFile())
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

    private static void copyLimited(InputStream input, FileOutputStream output, int maxBytes)
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
