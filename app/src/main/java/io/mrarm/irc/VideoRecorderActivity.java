package io.mrarm.irc;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/** CameraX recorder for short, broadly compatible H.264/AAC chat videos. */
public class VideoRecorderActivity extends ThemedActivity {
    private static final int MAX_DURATION_MS = 60_000;
    private static final long MAX_FILE_SIZE = 17L * 1024 * 1024;
    private static final int VIDEO_BITRATE_ESTIMATE = 2_000_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout previewContainer;
    private LinearLayout controls;
    private PreviewView previewView;
    private TextView status;
    private Button recordButton;
    private Button switchButton;
    private ProcessCameraProvider cameraProvider;
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private File output;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private boolean recording;
    private boolean reviewing;
    private boolean leaving;
    private boolean reviewWhenFinalized;
    private boolean discardWhenFinalized;
    private long startedAt;

    private final Runnable timer = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            long elapsed = System.currentTimeMillis() - startedAt;
            long bytes = output != null && output.exists() ? output.length() : 0;
            long estimated = elapsed * (VIDEO_BITRATE_ESTIMATE + 96_000L) / 8_000L;
            bytes = Math.max(bytes, estimated);
            status.setText(getString(R.string.video_recorder_status, formatDuration(elapsed),
                    formatMegabytes(bytes), formatMegabytes(MAX_FILE_SIZE)));
            if (elapsed >= MAX_DURATION_MS || bytes >= MAX_FILE_SIZE) {
                stopRecording(true);
                return;
            }
            handler.postDelayed(this, 250);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.video_recorder_title);
        if (!hasRecorderPermissions()) {
            Toast.makeText(this, R.string.video_recorder_permission_required, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        buildRecorderUi();
        initializeCamera();
    }

    private boolean hasRecorderPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void buildRecorderUi() {
        reviewing = false;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff000000);
        previewContainer = new FrameLayout(this);
        previewView = new PreviewView(this);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewContainer.addView(previewView, new FrameLayout.LayoutParams(-1, -1));
        status = new TextView(this);
        status.setTextColor(0xffffffff);
        status.setBackgroundColor(0x66000000);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(8), dp(12), dp(8));
        status.setText(R.string.video_recorder_ready);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        statusParams.topMargin = dp(12);
        previewContainer.addView(status, statusParams);
        root.addView(previewContainer, new LinearLayout.LayoutParams(-1, 0, 1));
        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(8), dp(8), dp(8), dp(8));
        switchButton = button(R.string.video_recorder_switch);
        recordButton = button(R.string.video_recorder_start);
        Button cancel = button(R.string.action_cancel);
        switchButton.setEnabled(false);
        recordButton.setEnabled(false);
        controls.addView(switchButton, weighted());
        controls.addView(recordButton, weighted());
        controls.addView(cancel, weighted());
        root.addView(controls);
        setContentView(root);
        recordButton.setOnClickListener(v -> {
            if (recording || activeRecording != null) stopRecording(true); else startRecording();
        });
        switchButton.setOnClickListener(v -> switchCamera());
        cancel.setOnClickListener(v -> cancelAndFinish());
    }

    private void initializeCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (Exception error) {
                Toast.makeText(this, R.string.video_recorder_camera_failed, Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (cameraProvider == null || previewView == null || reviewing || isFinishing()) return;
        try {
            cameraProvider.unbindAll();
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            QualitySelector quality = QualitySelector.from(Quality.HD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD));
            // Web viewers used by chat recipients commonly support AVC/H.264 but not HEVC.
            // Do not let a modern device silently prefer HEVC for these short shareable clips.
            Recorder recorder = new Recorder.Builder()
                    .setQualitySelector(quality)
                    .setVideoMimeType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    .build();
            videoCapture = VideoCapture.withOutput(recorder);
            CameraSelector selector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
            cameraProvider.bindToLifecycle(this, selector, preview, videoCapture);
            recordButton.setEnabled(true);
            switchButton.setEnabled(true);
        } catch (Exception error) {
            videoCapture = null;
            Toast.makeText(this, R.string.video_recorder_camera_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void switchCamera() {
        if (recording || activeRecording != null) return;
        lensFacing = lensFacing == CameraSelector.LENS_FACING_BACK
                ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        bindCamera();
    }

    private void startRecording() {
        if (videoCapture == null || recording || activeRecording != null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.video_recorder_permission_required, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            File directory = new File(getCacheDir(), "uploads");
            if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create upload directory");
            output = new File(directory, "video-" + System.currentTimeMillis() + ".mp4");
            FileOutputOptions options = new FileOutputOptions.Builder(output).setFileSizeLimit(MAX_FILE_SIZE).build();
            PendingRecording pending = videoCapture.getOutput().prepareRecording(this, options);
            reviewWhenFinalized = false;
            discardWhenFinalized = false;
            activeRecording = pending.withAudioEnabled().start(ContextCompat.getMainExecutor(this), this::onVideoRecordEvent);
            recordButton.setEnabled(false);
            switchButton.setEnabled(false);
        } catch (Exception error) {
            deleteOutput();
            Toast.makeText(this, R.string.video_recorder_start_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void onVideoRecordEvent(VideoRecordEvent event) {
        if (event instanceof VideoRecordEvent.Start) {
            recording = true;
            startedAt = System.currentTimeMillis();
            recordButton.setEnabled(true);
            recordButton.setText(R.string.video_recorder_stop);
            handler.post(timer);
        } else if (event instanceof VideoRecordEvent.Finalize) {
            VideoRecordEvent.Finalize finalize = (VideoRecordEvent.Finalize) event;
            finishRecording(finalize.getError() == VideoRecordEvent.Finalize.ERROR_NONE
                    || finalize.getError() == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED);
        }
    }

    private void stopRecording(boolean review) {
        if (activeRecording == null) return;
        reviewWhenFinalized = review;
        recordButton.setEnabled(false);
        activeRecording.stop();
    }

    private void finishRecording(boolean valid) {
        handler.removeCallbacks(timer);
        recording = false;
        if (activeRecording != null) {
            activeRecording.close();
            activeRecording = null;
        }
        if (discardWhenFinalized || leaving) {
            deleteOutput();
            return;
        }
        if (!valid || output == null || output.length() == 0) {
            deleteOutput();
            Toast.makeText(this, R.string.video_recorder_record_failed, Toast.LENGTH_LONG).show();
            resetRecorder();
        } else if (reviewWhenFinalized) {
            showReview();
        }
    }

    private void showReview() {
        reviewing = true;
        if (cameraProvider != null) cameraProvider.unbindAll();
        previewContainer.removeAllViews();
        VideoView video = new VideoView(this);
        MediaController mediaControls = new MediaController(this);
        mediaControls.setAnchorView(video);
        video.setMediaController(mediaControls);
        video.setVideoPath(output.getAbsolutePath());
        video.setOnPreparedListener(mp -> video.start());
        previewContainer.addView(video, new FrameLayout.LayoutParams(-1, -1));
        controls.removeAllViews();
        Button redo = button(R.string.video_recorder_redo);
        Button send = button(R.string.file_upload_send);
        Button cancel = button(R.string.action_cancel);
        controls.addView(redo, weighted());
        controls.addView(send, weighted());
        controls.addView(cancel, weighted());
        redo.setOnClickListener(v -> {
            video.stopPlayback();
            deleteOutput();
            resetRecorder();
        });
        send.setOnClickListener(v -> {
            video.stopPlayback();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", output);
            Intent result = new Intent();
            result.setData(uri);
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            leaving = true;
            setResult(RESULT_OK, result);
            finish();
        });
        cancel.setOnClickListener(v -> {
            video.stopPlayback();
            cancelAndFinish();
        });
    }

    private void resetRecorder() {
        handler.removeCallbacks(timer);
        recording = false;
        reviewing = false;
        activeRecording = null;
        buildRecorderUi();
        bindCamera();
    }

    private void releaseCamera() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        videoCapture = null;
    }

    private void deleteOutput() {
        if (output != null) output.delete();
        output = null;
    }

    private void cancelAndFinish() {
        leaving = true;
        discardWhenFinalized = true;
        handler.removeCallbacks(timer);
        if (activeRecording != null) activeRecording.stop(); else deleteOutput();
        releaseCamera();
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override public void onBackPressed() { cancelAndFinish(); }

    @Override protected void onPause() {
        super.onPause();
        if (!leaving && !reviewing && activeRecording != null) cancelAndFinish();
        else if (!reviewing) releaseCamera();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!reviewing && activeRecording == null) bindCamera();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(timer);
        if (activeRecording != null) activeRecording.stop();
        releaseCamera();
        if (!leaving) deleteOutput();
        super.onDestroy();
    }

    private Button button(int text) {
        Button button = new Button(this);
        button.setText(text);
        return button;
    }

    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }
    private static String formatMegabytes(long bytes) {
        return String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576f);
    }
}
