package io.mrarm.irc.dialog;

import android.app.Activity;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.upload.SimosnapUploader;

/** Records an AAC voice message, then offers playback, discard or upload. */
public final class VoiceRecorderDialog {

    private VoiceRecorderDialog() { }

    public static void show(Activity activity, ServerConnectionInfo connection, String target) {
        File directory = new File(activity.getCacheDir(), "uploads");
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(activity, R.string.voice_record_failed, Toast.LENGTH_LONG).show();
            return;
        }
        File file = new File(directory, "voice-" + System.currentTimeMillis() + ".m4a");
        MediaRecorder recorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new MediaRecorder(activity) : createLegacyRecorder();
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(file.getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (Exception error) {
            try { recorder.release(); } catch (Exception ignored) { }
            file.delete();
            Toast.makeText(activity, R.string.voice_record_failed, Toast.LENGTH_LONG).show();
            return;
        }

        TextView timer = new TextView(activity);
        int pad = (int) (24 * activity.getResources().getDisplayMetrics().density);
        timer.setPadding(pad, pad, pad, pad);
        timer.setTextSize(28);
        Handler handler = new Handler(Looper.getMainLooper());
        long started = System.currentTimeMillis();
        Runnable tick = new Runnable() {
            @Override public void run() {
                long seconds = (System.currentTimeMillis() - started) / 1000;
                timer.setText(String.format(Locale.getDefault(), "%02d:%02d",
                        seconds / 60, seconds % 60));
                handler.postDelayed(this, 500);
            }
        };
        handler.post(tick);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.voice_record_title)
                .setMessage(R.string.voice_record_in_progress)
                .setView(timer)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.voice_record_stop, null)
                .create();
        boolean[] finished = { false };
        Runnable finish = () -> {
            if (finished[0]) return;
            finished[0] = true;
            handler.removeCallbacks(tick);
            try { recorder.stop(); } catch (RuntimeException ignored) { }
            recorder.release();
        };
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                finish.run();
                dialog.dismiss();
                if (file.length() > 0) showReview(activity, connection, target, file);
                else file.delete();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                finish.run(); file.delete(); dialog.dismiss();
            });
        });
        dialog.setOnCancelListener(ignored -> { finish.run(); file.delete(); });
        dialog.show();
    }

    @SuppressWarnings("deprecation")
    private static MediaRecorder createLegacyRecorder() {
        return new MediaRecorder();
    }

    private static void showReview(Activity activity, ServerConnectionInfo connection,
                                   String target, File file) {
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button play = new Button(activity);
        play.setText(R.string.voice_record_play);
        actions.addView(play, new LinearLayout.LayoutParams(0, -2, 1));
        MediaPlayer[] player = { null };
        boolean[] sent = { false };
        play.setOnClickListener(v -> {
            try {
                if (player[0] == null) {
                    player[0] = new MediaPlayer();
                    player[0].setDataSource(file.getAbsolutePath());
                    player[0].prepare();
                    player[0].setOnCompletionListener(mp -> play.setText(
                            R.string.voice_record_play));
                }
                if (player[0].isPlaying()) {
                    player[0].pause(); play.setText(R.string.voice_record_play);
                } else {
                    player[0].start(); play.setText(R.string.voice_record_pause);
                }
            } catch (IOException error) {
                Toast.makeText(activity, R.string.voice_record_failed, Toast.LENGTH_SHORT).show();
            }
        });
        AlertDialog review = new AlertDialog.Builder(activity)
                .setTitle(R.string.voice_record_review)
                .setView(actions)
                .setNegativeButton(R.string.voice_record_discard, (d, w) -> file.delete())
                .setPositiveButton(R.string.file_upload_send, (d, w) -> {
                    sent[0] = true;
                    if (player[0] != null) player[0].release();
                    Uri uri = FileProvider.getUriForFile(activity,
                            activity.getPackageName() + ".fileprovider", file);
                    SimosnapUploader.confirmAndUpload(activity, connection, target, uri);
                }).create();
        review.setOnDismissListener(ignored -> {
            if (player[0] != null) {
                try { player[0].release(); } catch (Exception ignoredError) { }
                player[0] = null;
            }
            if (!sent[0])
                file.delete();
        });
        review.show();
    }
}
