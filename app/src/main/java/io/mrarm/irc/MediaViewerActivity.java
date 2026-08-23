package io.mrarm.irc;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** In-app viewer for image, audio and video links found in chat messages. */
public class MediaViewerActivity extends ThemedActivity {

    public static final String ARG_URL = "url";
    public static final String ARG_MIME = "mime";
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private AnimatedImageDrawable mAnimatedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getStringExtra(ARG_URL);
        String mime = getIntent().getStringExtra(ARG_MIME);
        if (url == null || mime == null) { finish(); return; }
        setTitle(R.string.media_viewer_title);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout actions = new LinearLayout(this);
        Button external = new Button(this);
        external.setText(R.string.media_open_external);
        external.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse(url))));
        Button share = new Button(this);
        share.setText(R.string.action_share);
        share.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, url);
            startActivity(Intent.createChooser(intent, getString(R.string.action_share)));
        });
        actions.addView(external, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(share, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);
        if (mime.startsWith("image/"))
            showImage(root, url);
        else
            showPlayer(root, url, mime.startsWith("audio/"));
        setContentView(root);
    }

    private void showImage(LinearLayout root, String url) {
        ProgressBar progress = new ProgressBar(this);
        root.addView(progress, new LinearLayout.LayoutParams(-1, 0, 1));
        EXECUTOR.execute(() -> {
            Drawable drawable = downloadImage(url);
            runOnUiThread(() -> {
                root.removeView(progress);
                if (drawable == null) {
                    Toast.makeText(this, R.string.media_open_failed, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setAdjustViewBounds(true);
                image.setImageDrawable(drawable);
                if (drawable instanceof AnimatedImageDrawable) {
                    mAnimatedImage = (AnimatedImageDrawable) drawable;
                    mAnimatedImage.start();
                }
                root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
            });
        });
    }

    private void showPlayer(LinearLayout root, String url, boolean audio) {
        if (audio) {
            TextView label = new TextView(this);
            label.setText(R.string.media_audio_label);
            label.setTextSize(24);
            label.setGravity(Gravity.CENTER);
            root.addView(label, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        VideoView player = new VideoView(this);
        MediaController controls = new MediaController(this);
        controls.setAnchorView(player);
        player.setMediaController(controls);
        player.setVideoURI(Uri.parse(url));
        player.setOnPreparedListener(mp -> player.start());
        player.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, R.string.media_open_failed, Toast.LENGTH_LONG).show();
            return true;
        });
        root.addView(player, new LinearLayout.LayoutParams(-1, audio ? -2 : 0,
                audio ? 0 : 1));
        player.requestFocus();
    }

    private Drawable downloadImage(String address) {
        HttpURLConnection connection = null;
        File file = new File(getCacheDir(), "preview-" + System.nanoTime());
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "RevolutionIRC/0.5.5");
            int length = connection.getContentLength();
            if (length > 20 * 1024 * 1024) return null;
            int total = 0;
            byte[] buffer = new byte[32768];
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(file)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > 20 * 1024 * 1024) return null;
                    output.write(buffer, 0, read);
                }
            }
            return ImageDecoder.decodeDrawable(ImageDecoder.createSource(file),
                    (decoder, info, source) -> {
                        int width = info.getSize().getWidth();
                        int height = info.getSize().getHeight();
                        float scale = Math.min(1f, 2048f / Math.max(width, height));
                        decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                                Math.max(1, Math.round(height * scale)));
                    });
        } catch (Exception error) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
            file.delete();
        }
    }

    @Override
    protected void onPause() {
        if (mAnimatedImage != null)
            mAnimatedImage.stop();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAnimatedImage != null)
            mAnimatedImage.start();
    }
}
