package io.mrarm.irc.upload;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.widget.Toast;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.CommandHandlerList;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.irc.ExtJwtCommandHandler;

/** Uploads a picked Android document to the SimosNap TUS endpoint and shares its URL. */
public final class SimosnapUploader {

    private static final String ENDPOINT = "https://media.simosnap.com";
    private static final int CHUNK_SIZE = 512 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private SimosnapUploader() { }

    public static void confirmAndUpload(Activity activity, ServerConnectionInfo connection,
                                        String targetNick, Uri uri) {
        if (!(connection.getApiInstance() instanceof IRCConnection))
            return;
        try {
            FileInfo file = readFileInfo(activity.getContentResolver(), uri);
            if (file.mime.startsWith("image/") && !file.mime.equals("image/gif")) {
                preparePrivateImage(activity, connection, targetNick, uri, file);
                return;
            }
            String validation = validate(file);
            if (validation != null) {
                Toast.makeText(activity, validation, Toast.LENGTH_LONG).show();
                return;
            }
            showConfirmation(activity, connection, targetNick, uri, file);
        } catch (IOException e) {
            Toast.makeText(activity, R.string.error_file_open, Toast.LENGTH_SHORT).show();
        }
    }

    private static void showConfirmation(Activity activity, ServerConnectionInfo connection,
                                         String targetNick, Uri uri, FileInfo file) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.file_upload_title)
                    .setMessage(activity.getString(R.string.file_upload_confirm,
                            file.name, formatSize(file.size), targetNick))
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_ok, (dialog, which) ->
                            acquireTokenAndUpload(activity, connection, targetNick, uri, file))
                    .show();
    }

    private static void preparePrivateImage(Activity activity, ServerConnectionInfo connection,
                                            String targetNick, Uri uri, FileInfo original) {
        ProgressUi progress = ProgressUi.show(activity, 0,
                R.string.file_private_image_progress, false);
        EXECUTOR.execute(() -> {
            try {
                File output = compressImage(activity, uri);
                Uri cleanUri = FileProvider.getUriForFile(activity,
                        activity.getPackageName() + ".fileprovider", output);
                FileInfo clean = new FileInfo(cleanImageName(original.name), "image/jpeg",
                        output.length());
                String validation = validate(clean);
                activity.runOnUiThread(() -> {
                    progress.dismiss(activity);
                    if (validation != null)
                        Toast.makeText(activity, validation, Toast.LENGTH_LONG).show();
                    else
                        showConfirmation(activity, connection, targetNick, cleanUri, clean);
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    progress.dismiss(activity);
                    Toast.makeText(activity, R.string.file_compress_failed,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static File compressImage(Activity activity, Uri uri) throws IOException {
        File directory = new File(activity.getCacheDir(), "uploads");
        if (!directory.exists() && !directory.mkdirs())
            throw new IOException("Cartella temporanea non disponibile");
        File source = File.createTempFile("source_", ".image", directory);
        try (InputStream input = activity.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(source)) {
            if (input == null)
                throw new IOException("Immagine non leggibile");
            byte[] copyBuffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(copyBuffer)) != -1)
                output.write(copyBuffer, 0, count);
        }
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try {
            orientation = new ExifInterface(source.getAbsolutePath()).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) { }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            throw new IOException("Immagine non leggibile");
        int sample = 1;
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048)
            sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap;
        bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        source.delete();
        if (bitmap == null)
            throw new IOException("Immagine non leggibile");
        Matrix matrix = new Matrix();
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) matrix.postRotate(90);
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) matrix.postRotate(180);
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) matrix.postRotate(270);
        else if (orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL) matrix.postScale(-1, 1);
        else if (orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL) matrix.postScale(1, -1);
        if (!matrix.isIdentity()) {
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
                bitmap = rotated;
            }
        }
        File output = File.createTempFile("image_", ".jpg", directory);
        try (FileOutputStream stream = new FileOutputStream(output)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 84, stream))
                throw new IOException("Compressione non riuscita");
        } finally {
            bitmap.recycle();
        }
        return output;
    }

    private static String cleanImageName(String original) {
        int dot = original.lastIndexOf('.');
        String base = dot > 0 ? original.substring(0, dot) : original;
        return base + "_private.jpg";
    }

    private static void acquireTokenAndUpload(Activity activity, ServerConnectionInfo connection,
                                               String targetNick, Uri uri, FileInfo file) {
        IRCConnection irc = (IRCConnection) connection.getApiInstance();
        CommandHandlerList handlers = irc.getServerConnectionData().getCommandHandlerList();
        ExtJwtCommandHandler handler = handlers.getHandler(ExtJwtCommandHandler.class);
        if (handler == null) {
            handler = new ExtJwtCommandHandler();
            handlers.registerHandler(handler);
        }
        AtomicBoolean started = new AtomicBoolean(false);
        ExtJwtCommandHandler.Callback callback = token -> {
            if (started.compareAndSet(false, true))
                startUpload(activity, irc, targetNick, uri, file, token);
        };
        handler.request(callback);
        ExtJwtCommandHandler finalHandler = handler;
        irc.sendCommandRaw("EXTJWT *", null, error -> {
            if (started.compareAndSet(false, true)) {
                finalHandler.cancel(callback);
                startUpload(activity, irc, targetNick, uri, file, null);
            }
        });
        // Networks without EXTJWT may not send an error the old library understands.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (started.compareAndSet(false, true)) {
                finalHandler.cancel(callback);
                startUpload(activity, irc, targetNick, uri, file, null);
            }
        }, 6000);
    }

    private static void startUpload(Activity activity, IRCConnection irc, String targetNick,
                                    Uri uri, FileInfo file, String token) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread(() -> startUpload(activity, irc, targetNick, uri, file, token));
            return;
        }
        UploadControl control = new UploadControl();
        ProgressUi progress = ProgressUi.show(activity, R.string.file_upload_title,
                R.string.file_upload_progress, true, control::cancel);
        EXECUTOR.execute(() -> {
            try {
                String url = uploadTus(activity.getContentResolver(), uri, file, token, control,
                        sent -> activity.runOnUiThread(() ->
                                progress.setProgress(file.size > 0 ?
                                        (int) (sent * 100 / file.size) : 0)));
                String publicUrl = toFriendlyUrl(url);
                irc.sendMessage(targetNick, "File condiviso: " + publicUrl, response ->
                        activity.runOnUiThread(() -> {
                            progress.dismiss(activity);
                            Toast.makeText(activity, R.string.file_upload_sent, Toast.LENGTH_LONG).show();
                        }), error -> activity.runOnUiThread(() -> {
                    progress.dismiss(activity);
                    Toast.makeText(activity, activity.getString(R.string.file_upload_send_failed,
                            publicUrl), Toast.LENGTH_LONG).show();
                }));
            } catch (UploadCancelledException ignored) {
                activity.runOnUiThread(() -> progress.dismiss(activity));
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    progress.dismiss(activity);
                    Toast.makeText(activity, activity.getString(R.string.file_upload_failed,
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static String uploadTus(ContentResolver resolver, Uri uri, FileInfo file, String token,
                                    UploadControl control, ProgressListener listener) throws IOException {
        HttpURLConnection create = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        URL uploadUrl;
        try {
            control.use(create);
            configureTimeouts(create);
            create.setRequestMethod("POST");
            create.setRequestProperty("Tus-Resumable", "1.0.0");
            create.setRequestProperty("Upload-Length", String.valueOf(file.size));
            String metadata = metadata("filename", file.name) + "," + metadata("filetype", file.mime);
            if (token != null && !token.isEmpty())
                metadata += "," + metadata("extjwt", token);
            create.setRequestProperty("Upload-Metadata", metadata);
            create.setDoOutput(true);
            create.setFixedLengthStreamingMode(0);
            create.getOutputStream().close();
            control.checkCancelled();
            int status = create.getResponseCode();
            if (status != 201)
                throw new IOException("TUS create HTTP " + status);
            String location = create.getHeaderField("Location");
            if (location == null)
                throw new IOException("TUS Location mancante");
            uploadUrl = new URL(new URL(ENDPOINT), location);
        } finally {
            control.clear(create);
            create.disconnect();
        }

        long offset = 0;
        byte[] buffer = new byte[CHUNK_SIZE];
        try (InputStream input = new BufferedInputStream(resolver.openInputStream(uri))) {
            if (input == null)
                throw new IOException("File non leggibile");
            int read;
            while ((read = input.read(buffer)) != -1) {
                control.checkCancelled();
                HttpURLConnection patch = (HttpURLConnection) uploadUrl.openConnection();
                try {
                    control.use(patch);
                    configureTimeouts(patch);
                    setPatchMethod(patch);
                    patch.setDoOutput(true);
                    patch.setRequestProperty("Tus-Resumable", "1.0.0");
                    patch.setRequestProperty("Upload-Offset", String.valueOf(offset));
                    patch.setRequestProperty("Content-Type", "application/offset+octet-stream");
                    patch.setFixedLengthStreamingMode(read);
                    try (OutputStream output = patch.getOutputStream()) {
                        output.write(buffer, 0, read);
                    }
                    control.checkCancelled();
                    int patchStatus = patch.getResponseCode();
                    if (patchStatus != 204)
                        throw new IOException("TUS patch HTTP " + patchStatus);
                    String serverOffset = patch.getHeaderField("Upload-Offset");
                    offset = serverOffset != null ? Long.parseLong(serverOffset) : offset + read;
                } finally {
                    control.clear(patch);
                    patch.disconnect();
                }
                listener.onProgress(offset);
            }
        }
        return uploadUrl.toString();
    }

    private static void configureTimeouts(HttpURLConnection connection) {
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
    }

    private static void setPatchMethod(HttpURLConnection connection) throws ProtocolException {
        try {
            connection.setRequestMethod("PATCH");
        } catch (ProtocolException unsupportedPatch) {
            // Android versions whose HttpURLConnection predates PATCH can use the TUS override.
            connection.setRequestMethod("POST");
            connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        }
    }

    private static FileInfo readFileInfo(ContentResolver resolver, Uri uri) throws IOException {
        String name = "file";
        long size = -1;
        try (Cursor cursor = resolver.query(uri, new String[] {
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex))
                    name = cursor.getString(nameIndex);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex))
                    size = cursor.getLong(sizeIndex);
            }
        }
        if (size < 0) {
            try (android.os.ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r")) {
                if (descriptor != null)
                    size = descriptor.getStatSize();
            }
        }
        if (size < 0)
            throw new IOException("Dimensione sconosciuta");
        String mime = resolver.getType(uri);
        if (mime == null)
            mime = "application/octet-stream";
        return new FileInfo(name.replace('\r', '_').replace('\n', '_'), mime, size);
    }

    private static String validate(FileInfo file) {
        long limit;
        if (file.mime.startsWith("image/")) limit = 8L * 1024 * 1024;
        else if (file.mime.startsWith("video/")) limit = 18L * 1024 * 1024;
        else if (file.mime.startsWith("audio/")) limit = 12L * 1024 * 1024;
        else if (file.mime.equals("application/pdf")) limit = 1024L * 1024;
        else if (file.mime.equals("text/plain")) limit = 500L * 1024;
        else return "Tipo di file non supportato da SimosNap";
        if (file.size > limit)
            return "File troppo grande per questo tipo (massimo " + formatSize(limit) + ")";
        return null;
    }

    private static String metadata(String name, String value) {
        return name + " " + Base64.encodeToString(value.getBytes(Charset.forName("UTF-8")),
                Base64.NO_WRAP);
    }

    private static String toFriendlyUrl(String raw) {
        String prefix = "https://media.simosnap.com/";
        return raw.startsWith(prefix) ? prefix + "files/" + raw.substring(prefix.length()) : raw;
    }

    private static String formatSize(long size) {
        if (size >= 1024 * 1024)
            return String.format(Locale.getDefault(), "%.1f MB", size / 1048576f);
        return String.format(Locale.getDefault(), "%.1f KB", size / 1024f);
    }

    private interface ProgressListener { void onProgress(long sent); }

    private static class UploadCancelledException extends IOException { }

    private static class UploadControl {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile HttpURLConnection activeConnection;

        void use(HttpURLConnection connection) throws UploadCancelledException {
            activeConnection = connection;
            checkCancelled();
        }

        void clear(HttpURLConnection connection) {
            if (activeConnection == connection)
                activeConnection = null;
        }

        void cancel() {
            cancelled.set(true);
            HttpURLConnection connection = activeConnection;
            if (connection != null)
                connection.disconnect();
        }

        void checkCancelled() throws UploadCancelledException {
            if (cancelled.get())
                throw new UploadCancelledException();
        }
    }

    private static class ProgressUi {
        private final AlertDialog dialog;
        private final ProgressBar progressBar;

        private ProgressUi(AlertDialog dialog, ProgressBar progressBar) {
            this.dialog = dialog;
            this.progressBar = progressBar;
        }

        static ProgressUi show(Activity activity, int titleRes, int messageRes,
                               boolean horizontal) {
            return show(activity, titleRes, messageRes, horizontal, null);
        }

        static ProgressUi show(Activity activity, int titleRes, int messageRes,
                               boolean horizontal, Runnable onCancel) {
            ProgressBar bar = horizontal
                    ? new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal)
                    : new ProgressBar(activity);
            bar.setIndeterminate(!horizontal);
            if (horizontal)
                bar.setMax(100);
            int padding = (int) (24 * activity.getResources().getDisplayMetrics().density);
            bar.setPadding(padding, padding, padding, padding);
            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                    .setMessage(messageRes)
                    .setView(bar)
                    .setCancelable(false);
            if (onCancel != null)
                builder.setNegativeButton(R.string.action_cancel, (dialog, which) -> onCancel.run());
            if (titleRes != 0)
                builder.setTitle(titleRes);
            AlertDialog dialog = builder.create();
            dialog.show();
            return new ProgressUi(dialog, bar);
        }

        void setProgress(int progress) {
            if (dialog.isShowing())
                progressBar.setProgress(progress);
        }

        void dismiss(Activity activity) {
            if (!activity.isFinishing() && !activity.isDestroyed() && dialog.isShowing())
                dialog.dismiss();
        }
    }

    private static class FileInfo {
        final String name;
        final String mime;
        final long size;
        FileInfo(String name, String mime, long size) {
            this.name = name;
            this.mime = mime;
            this.size = size;
        }
    }
}
