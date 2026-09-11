package io.mrarm.irc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Small self-updater for the official GitHub Releases build.
 *
 * The automatic mode only checks for a newer release. It never downloads or installs anything
 * until the user explicitly presses Update. Android's package installer remains the final gate.
 */
public final class UpdateManager {

    private static final String RELEASE_API =
            "https://api.github.com/repos/TIARCA/TIARCA/releases/latest";
    private static final String PREFS = "tiarca_updater";
    private static final String PREF_CHOICE_MADE = "choice_made";
    private static final String PREF_AUTOMATIC = "automatic";
    private static final String PREF_LAST_ATTEMPT = "last_attempt";
    private static final String PREF_LAST_SUCCESS = "last_success";
    private static final long CHECK_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L;

    private static boolean sStartupHandled;
    private static boolean sCheckInProgress;

    private UpdateManager() {
    }

    public static void maybePromptAndCheck(Activity activity) {
        if (sStartupHandled || activity == null || activity.isFinishing() || activity.isDestroyed())
            return;
        sStartupHandled = true;

        activity.getWindow().getDecorView().post(() -> {
            if (activity.isFinishing() || activity.isDestroyed())
                return;
            SharedPreferences preferences = prefs(activity);
            if (!preferences.getBoolean(PREF_CHOICE_MADE, false)) {
                showOptIn(activity);
                return;
            }
            if (!preferences.getBoolean(PREF_AUTOMATIC, false))
                return;
            long lastAttempt = preferences.getLong(PREF_LAST_ATTEMPT, 0L);
            if (System.currentTimeMillis() - lastAttempt >= CHECK_INTERVAL_MS)
                checkForUpdates(activity, false);
        });
    }

    public static void showAboutDialog(Activity activity) {
        SharedPreferences preferences = prefs(activity);
        int padding = dp(activity, 20);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, 0, padding, 0);

        TextView body = new TextView(activity);
        body.setText(activity.getString(R.string.about_body, BuildConfig.VERSION_NAME));
        body.setTextIsSelectable(true);
        content.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button checkNow = new Button(activity);
        checkNow.setText(text(activity, "Cerca aggiornamenti", "Check for updates"));
        LinearLayout.LayoutParams checkNowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        checkNowParams.gravity = Gravity.CENTER_HORIZONTAL;
        checkNowParams.topMargin = dp(activity, 14);
        content.addView(checkNow, checkNowParams);

        CheckBox automatic = new CheckBox(activity);
        automatic.setText(text(activity,
                "Controlla aggiornamenti",
                "Check for updates automatically"));
        automatic.setSingleLine(true);
        automatic.setChecked(preferences.getBoolean(PREF_AUTOMATIC, false));
        LinearLayout.LayoutParams automaticParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        automaticParams.topMargin = dp(activity, 4);
        content.addView(automatic, automaticParams);

        TextView lastCheck = new TextView(activity);
        lastCheck.setPadding(0, dp(activity, 6), 0, 0);
        lastCheck.setText(lastCheckText(activity, preferences));
        content.addView(lastCheck, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        automatic.setOnCheckedChangeListener((buttonView, isChecked) ->
                preferences.edit()
                        .putBoolean(PREF_CHOICE_MADE, true)
                        .putBoolean(PREF_AUTOMATIC, isChecked)
                        .apply());
        checkNow.setOnClickListener(v -> checkForUpdates(activity, true));

        new AlertDialog.Builder(activity)
                .setTitle(R.string.about_title)
                .setView(content)
                .setNeutralButton(R.string.about_revolution_github, (dialog, which) ->
                        activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/MCMrARM/revolution-irc"))))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public static void checkForUpdates(Activity activity, boolean manual) {
        synchronized (UpdateManager.class) {
            if (sCheckInProgress) {
                if (manual)
                    Toast.makeText(activity,
                            text(activity, "Controllo già in corso…", "Update check already in progress…"),
                            Toast.LENGTH_SHORT).show();
                return;
            }
            sCheckInProgress = true;
        }

        SharedPreferences preferences = prefs(activity);
        preferences.edit().putLong(PREF_LAST_ATTEMPT, System.currentTimeMillis()).apply();
        if (manual)
            Toast.makeText(activity,
                    text(activity, "Controllo aggiornamenti…", "Checking for updates…"),
                    Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                ReleaseInfo release = fetchLatestRelease();
                long now = System.currentTimeMillis();
                preferences.edit()
                        .putLong(PREF_LAST_ATTEMPT, now)
                        .putLong(PREF_LAST_SUCCESS, now)
                        .apply();
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed())
                        return;
                    if (compareVersions(release.version, BuildConfig.VERSION_NAME) > 0)
                        showUpdateAvailable(activity, release);
                    else if (manual)
                        showMessage(activity,
                                text(activity, "TIARCA è aggiornata", "TIARCA is up to date"),
                                text(activity,
                                        "Hai già l'ultima versione disponibile: " + BuildConfig.VERSION_NAME + ".",
                                        "You already have the latest available version: " +
                                                BuildConfig.VERSION_NAME + "."));
                });
            } catch (Exception e) {
                if (manual) {
                    activity.runOnUiThread(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed())
                            showMessage(activity,
                                    text(activity, "Aggiornamenti", "Updates"),
                                    text(activity,
                                            "Impossibile verificare gli aggiornamenti. Controlla la connessione e riprova.",
                                            "Unable to check for updates. Check your connection and try again."));
                    });
                }
            } finally {
                synchronized (UpdateManager.class) {
                    sCheckInProgress = false;
                }
            }
        }, "TIARCA update check").start();
    }

    private static void showOptIn(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(text(activity, "Aggiornamenti TIARCA", "TIARCA updates"))
                .setMessage(text(activity,
                        "Vuoi che TIARCA controlli periodicamente la disponibilità di nuove versioni? " +
                                "Il controllo viene effettuato su GitHub circa una volta alla settimana. " +
                                "Nessun aggiornamento viene scaricato o installato senza una tua conferma. " +
                                "Se TIARCA è stata installata da F-Droid, usare questo aggiornamento significa bypassare i controlli di F-Droid.",
                        "Would you like TIARCA to periodically check for new versions? " +
                                "The check is performed on GitHub about once a week. " +
                                "No update is downloaded or installed without your confirmation. " +
                                "If TIARCA was installed from F-Droid, using this updater bypasses F-Droid's checks."))
                .setNegativeButton(text(activity, "No", "No"), (dialog, which) ->
                        prefs(activity).edit()
                                .putBoolean(PREF_CHOICE_MADE, true)
                                .putBoolean(PREF_AUTOMATIC, false)
                                .apply())
                .setPositiveButton(text(activity, "Sì", "Yes"), (dialog, which) -> {
                    prefs(activity).edit()
                            .putBoolean(PREF_CHOICE_MADE, true)
                            .putBoolean(PREF_AUTOMATIC, true)
                            .putLong(PREF_LAST_ATTEMPT, 0L)
                            .apply();
                    checkForUpdates(activity, false);
                })
                .show();
    }

    private static ReleaseInfo fetchLatestRelease() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "TIARCA/" + BuildConfig.VERSION_NAME);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK)
                throw new IllegalStateException("GitHub returned HTTP " + status);
            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null)
                    json.append(line);
            }

            JsonObject root = new JsonParser().parse(json.toString()).getAsJsonObject();
            String tag = root.get("tag_name").getAsString();
            String version = tag.startsWith("v") ? tag.substring(1) : tag;
            String body = root.has("body") && !root.get("body").isJsonNull()
                    ? root.get("body").getAsString() : "";
            String apkUrl = null;
            String apkName = null;
            JsonArray assets = root.getAsJsonArray("assets");
            if (assets != null) {
                for (JsonElement element : assets) {
                    JsonObject asset = element.getAsJsonObject();
                    String name = asset.get("name").getAsString();
                    if (name.toLowerCase(Locale.ROOT).endsWith(".apk") &&
                            name.toLowerCase(Locale.ROOT).contains("tiarca")) {
                        apkName = name;
                        apkUrl = asset.get("browser_download_url").getAsString();
                        break;
                    }
                }
            }
            if (TextUtils.isEmpty(apkUrl))
                throw new IllegalStateException("Release has no TIARCA APK asset");
            return new ReleaseInfo(version, body, apkName, apkUrl);
        } finally {
            connection.disconnect();
        }
    }

    private static void showUpdateAvailable(Activity activity, ReleaseInfo release) {
        String message = text(activity,
                "Versione installata: " + BuildConfig.VERSION_NAME + "\n" +
                        "Nuova versione: " + release.version + "\n\n" +
                        "L'aggiornamento verrà scaricato da GitHub. Se TIARCA è stata installata da F-Droid, procedere significa bypassare i controlli di F-Droid.",
                "Installed version: " + BuildConfig.VERSION_NAME + "\n" +
                        "New version: " + release.version + "\n\n" +
                        "The update will be downloaded from GitHub. If TIARCA was installed from F-Droid, continuing bypasses F-Droid's checks.");
        if (!TextUtils.isEmpty(release.notes)) {
            String notes = release.notes.length() > 3500
                    ? release.notes.substring(0, 3500) + "…" : release.notes;
            message += "\n\n" + notes;
        }
        new AlertDialog.Builder(activity)
                .setTitle(text(activity,
                        "È disponibile TIARCA " + release.version,
                        "TIARCA " + release.version + " is available"))
                .setMessage(message)
                .setNegativeButton(text(activity, "Più tardi", "Later"), null)
                .setPositiveButton(text(activity, "Aggiorna", "Update"), (dialog, which) ->
                        downloadAndInstall(activity, release))
                .show();
    }

    private static void downloadAndInstall(Activity activity, ReleaseInfo release) {
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle(text(activity, "Aggiornamento TIARCA", "TIARCA update"))
                .setMessage(text(activity, "Download in corso…", "Downloading…"))
                .setCancelable(false)
                .show();

        new Thread(() -> {
            try {
                File updatesDir = new File(activity.getCacheDir(), "updates");
                if (!updatesDir.exists() && !updatesDir.mkdirs())
                    throw new IllegalStateException("Unable to create update directory");
                File[] oldFiles = updatesDir.listFiles();
                if (oldFiles != null) {
                    for (File old : oldFiles)
                        old.delete();
                }
                File apk = new File(updatesDir, sanitizeFileName(release.apkName));
                downloadFile(release.apkUrl, apk);
                if (!isSignedByInstalledApp(activity, apk)) {
                    apk.delete();
                    throw new SecurityException("APK signing certificate does not match");
                }
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    launchInstaller(activity, apk);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (!activity.isFinishing() && !activity.isDestroyed())
                        showMessage(activity,
                                text(activity, "Aggiornamento non riuscito", "Update failed"),
                                text(activity,
                                        "Non è stato possibile scaricare o verificare l'APK ufficiale. " +
                                                "Nessun file è stato installato.",
                                        "The official APK could not be downloaded or verified. " +
                                                "Nothing was installed."));
                });
            }
        }, "TIARCA update download").start();
    }

    private static void downloadFile(String source, File target) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "TIARCA/" + BuildConfig.VERSION_NAME);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300)
                throw new IllegalStateException("Download returned HTTP " + status);
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[32768];
                int read;
                while ((read = input.read(buffer)) != -1)
                    output.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static boolean isSignedByInstalledApp(Context context, File apk) throws Exception {
        PackageManager manager = context.getPackageManager();
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(),
                PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo candidate = manager.getPackageArchiveInfo(apk.getAbsolutePath(),
                PackageManager.GET_SIGNING_CERTIFICATES);
        if (candidate == null || candidate.signingInfo == null || installed.signingInfo == null ||
                !context.getPackageName().equals(candidate.packageName))
            return false;

        Signature[] trusted = installed.signingInfo.hasMultipleSigners()
                ? installed.signingInfo.getApkContentsSigners()
                : installed.signingInfo.getSigningCertificateHistory();
        Signature[] incoming = candidate.signingInfo.getApkContentsSigners();
        if (incoming == null || incoming.length == 0)
            return false;
        for (Signature signature : incoming) {
            boolean found = false;
            for (Signature trustedSignature : trusted) {
                if (signature.equals(trustedSignature)) {
                    found = true;
                    break;
                }
            }
            if (!found)
                return false;
        }
        return true;
    }

    private static void launchInstaller(Activity activity, File apk) {
        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            showMessage(activity,
                    text(activity, "Installazione", "Installation"),
                    text(activity,
                            "Android non ha potuto aprire l'installer. Verifica che TIARCA sia autorizzata a installare app da questa origine.",
                            "Android could not open the installer. Check that TIARCA is allowed to install apps from this source."));
        }
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.replaceFirst("^[vV]", "").split("[.-]");
        String[] b = right.replaceFirst("^[vV]", "").split("[.-]");
        int count = Math.max(a.length, b.length);
        for (int i = 0; i < count; i++) {
            int av = i < a.length ? numericPrefix(a[i]) : 0;
            int bv = i < b.length ? numericPrefix(b[i]) : 0;
            if (av != bv)
                return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int numericPrefix(String value) {
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end)))
            end++;
        if (end == 0)
            return 0;
        try {
            return Integer.parseInt(value.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String lastCheckText(Context context, SharedPreferences preferences) {
        long last = preferences.getLong(PREF_LAST_SUCCESS, 0L);
        if (last == 0L)
            return text(context, "Ultimo controllo: mai", "Last check: never");
        String when = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(last));
        return text(context, "Ultimo controllo: " + when, "Last check: " + when);
    }

    private static void showMessage(Activity activity, String title, String message) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String text(Context context, String italian, String english) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        return "it".equals(locale.getLanguage()) ? italian : english;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static String sanitizeFileName(String name) {
        String safe = name == null ? "TIARCA-update.apk" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.toLowerCase(Locale.ROOT).endsWith(".apk") ? safe : safe + ".apk";
    }

    private static final class ReleaseInfo {
        final String version;
        final String notes;
        final String apkName;
        final String apkUrl;

        ReleaseInfo(String version, String notes, String apkName, String apkUrl) {
            this.version = version;
            this.notes = notes;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
        }
    }
}
