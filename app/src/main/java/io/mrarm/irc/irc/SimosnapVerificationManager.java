package io.mrarm.irc.irc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.irc.MainActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.util.WarningHelper;

/** Presents SimosNap's guest human-verification challenge as an actionable dialog. */
public final class SimosnapVerificationManager {

    private static final Pattern IRC_FORMATTING = Pattern.compile(
            "\\u0003\\d{0,2}(?:,\\d{1,2})?|[\\u0002\\u000f\\u0016\\u001d\\u001f]");
    private static final Pattern SOLVE_COMMAND = Pattern.compile(
            "(?i)/(?:raw\\s+)?(?:quote\\s+)?solve\\s+([^\\s\\u0000-\\u001f]+)");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,;:!?)\\]}>\\\"']+$");

    private static final int MAX_CONTEXT_NOTICES = 2;
    private static final long CONTEXT_MAX_AGE_MS = 15_000L;

    private final ServerConnectionInfo connection;
    private final Deque<RecentNotice> recentNotices = new ArrayDeque<>();
    private String recentNoticeServer;
    private VerificationWarning activeWarning;

    public SimosnapVerificationManager(ServerConnectionInfo connection) {
        this.connection = connection;
    }

    public void observeServerNotice(String serverName, String text) {
        if (!isSimosnapServerName(serverName))
            return;
        String normalized = normalizeNotice(text);
        if (normalized.isEmpty())
            return;

        Challenge parsed = parseChallenge(normalized);
        VerificationWarning warning;
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (recentNoticeServer == null || !recentNoticeServer.equalsIgnoreCase(serverName)) {
                recentNotices.clear();
                recentNoticeServer = serverName;
            }
            pruneRecentNotices(now);

            if (parsed == null) {
                recentNotices.addLast(new RecentNotice(normalized, now));
                while (recentNotices.size() > MAX_CONTEXT_NOTICES)
                    recentNotices.removeFirst();
                return;
            }

            String[] context = new String[recentNotices.size()];
            int i = 0;
            for (RecentNotice notice : recentNotices)
                context[i++] = notice.text;
            recentNotices.clear();

            Challenge challenge = withContext(parsed, context);
            if (activeWarning != null)
                return;
            warning = new VerificationWarning(challenge);
            activeWarning = warning;
        }
        WarningHelper.showWarning(warning);
    }

    private void pruneRecentNotices(long now) {
        while (!recentNotices.isEmpty() &&
                now - recentNotices.peekFirst().timestamp > CONTEXT_MAX_AGE_MS)
            recentNotices.removeFirst();
    }

    private synchronized void onWarningDismissed(VerificationWarning warning) {
        if (activeWarning == warning)
            activeWarning = null;
    }

    static boolean isSimosnapServerName(String serverName) {
        if (serverName == null)
            return false;
        String host = serverName.trim().toLowerCase(Locale.ROOT);
        return host.equals("simosnap.org") || host.endsWith(".simosnap.org") ||
                host.equals("simosnap.com") || host.endsWith(".simosnap.com");
    }

    static Challenge parseChallenge(String text) {
        String normalized = normalizeNotice(text);
        if (normalized.isEmpty())
            return null;
        Matcher matcher = SOLVE_COMMAND.matcher(normalized);
        if (!matcher.find())
            return null;

        String answer = matcher.group(1);
        if (answer != null)
            answer = TRAILING_PUNCTUATION.matcher(answer).replaceAll("");
        if (isPlaceholder(answer))
            answer = null;
        return new Challenge(normalized, answer);
    }

    static Challenge withContext(Challenge challenge, String... previousNotices) {
        if (challenge == null)
            return null;
        StringBuilder message = new StringBuilder();
        if (previousNotices != null) {
            for (String notice : previousNotices) {
                String normalized = normalizeNotice(notice);
                if (normalized.isEmpty())
                    continue;
                if (message.length() > 0)
                    message.append('\n');
                message.append(normalized);
            }
        }
        if (message.length() > 0)
            message.append('\n');
        message.append(challenge.message);
        return new Challenge(message.toString(), challenge.suggestedAnswer);
    }

    private static String normalizeNotice(String text) {
        if (text == null)
            return "";
        return IRC_FORMATTING.matcher(text).replaceAll("").trim();
    }

    private static boolean isPlaceholder(String answer) {
        if (answer == null || answer.isEmpty())
            return true;
        String lower = answer.toLowerCase(Locale.ROOT);
        return answer.charAt(0) == '<' || answer.charAt(0) == '[' || answer.charAt(0) == '{' ||
                lower.contains("answer") || lower.contains("risposta") ||
                lower.contains("result") || lower.contains("solution");
    }

    private static final class RecentNotice {
        final String text;
        final long timestamp;

        RecentNotice(String text, long timestamp) {
            this.text = text;
            this.timestamp = timestamp;
        }
    }

    static final class Challenge {
        final String message;
        final String suggestedAnswer;

        Challenge(String message, String suggestedAnswer) {
            this.message = message;
            this.suggestedAnswer = suggestedAnswer;
        }
    }

    private final class VerificationWarning extends WarningHelper.Warning {

        private final Challenge challenge;
        private AlertDialog dialog;
        private EditText answerField;
        private String draftAnswer;

        VerificationWarning(Challenge challenge) {
            this.challenge = challenge;
            this.draftAnswer = challenge.suggestedAnswer;
        }

        @Override
        public void showDialog(Activity activity) {
            super.showDialog(activity);
            dismissDialog(activity);

            answerField = new EditText(activity);
            answerField.setSingleLine(true);
            answerField.setHint(R.string.simosnap_verification_answer_hint);
            answerField.setInputType(InputType.TYPE_CLASS_TEXT |
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            if (draftAnswer != null)
                answerField.setText(draftAnswer);
            answerField.setSelection(answerField.getText().length());

            FrameLayout fieldContainer = new FrameLayout(activity);
            int margin = Math.round(24 * activity.getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            params.leftMargin = margin;
            params.rightMargin = margin;
            fieldContainer.addView(answerField, params);

            dialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.simosnap_verification_title)
                    .setMessage(activity.getString(R.string.simosnap_verification_message,
                            challenge.message))
                    .setView(fieldContainer)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.simosnap_verification_action, null)
                    .create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> submit(activity)));
            dialog.setOnDismissListener(ignored -> {
                dialog = null;
                answerField = null;
                dismissNotification(activity.getApplicationContext());
                dismiss();
                onWarningDismissed(this);
            });
            dialog.show();
        }

        private void submit(Activity activity) {
            if (answerField == null)
                return;
            String answer = answerField.getText().toString().trim();
            if (answer.isEmpty() || answer.indexOf('\r') >= 0 || answer.indexOf('\n') >= 0) {
                answerField.setError(activity.getString(R.string.simosnap_verification_answer_error));
                return;
            }
            if (!(connection.getApiInstance() instanceof IRCConnection) || !connection.isConnected()) {
                answerField.setError(activity.getString(R.string.simosnap_verification_send_failed));
                return;
            }

            draftAnswer = answer;
            IRCConnection irc = (IRCConnection) connection.getApiInstance();
            irc.sendCommandRaw("SOLVE " + answer, ignored -> activity.runOnUiThread(() -> {
                Toast.makeText(activity.getApplicationContext(),
                        R.string.simosnap_verification_sent, Toast.LENGTH_SHORT).show();
                if (dialog != null)
                    dialog.dismiss();
            }), error -> activity.runOnUiThread(() -> {
                if (answerField != null)
                    answerField.setError(activity.getString(
                            R.string.simosnap_verification_send_failed));
            }));
        }

        @Override
        public void dismissDialog(Activity activity) {
            if (answerField != null)
                draftAnswer = answerField.getText().toString();
            if (dialog != null) {
                dialog.setOnDismissListener(null);
                dialog.dismiss();
                dialog = null;
            }
            answerField = null;
        }

        @Override
        protected void buildNotification(Context context, NotificationCompat.Builder notification,
                                         int notificationId) {
            super.buildNotification(context, notification, notificationId);
            notification.setContentTitle(context.getString(R.string.simosnap_verification_title));
            notification.setContentText(context.getString(
                    R.string.simosnap_verification_notification));
            notification.setContentIntent(PendingIntent.getActivity(context, notificationId,
                    MainActivity.getLaunchIntent(context, connection, null),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
    }
}
