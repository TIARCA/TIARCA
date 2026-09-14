package io.mrarm.irc.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.mrarm.irc.BuildConfig;

/**
 * Opt-in diagnostic logger for user-shareable troubleshooting logs.
 *
 * The logger is deliberately independent from Logcat: when disabled it performs no file I/O and
 * lazy message suppliers are not evaluated. When enabled it writes a small rotating private log,
 * applying a final sanitization pass in addition to the rule that callers must never pass chat
 * contents or credentials in the first place.
 */
public final class DiagnosticLog {

    private static final String PREFS_NAME = "tiarca_diagnostics";
    private static final String PREF_ENABLED = "enabled";
    private static final String DIR_NAME = "diagnostics";
    private static final String LOG_NAME = "tiarca-debug.log";
    private static final String OLD_LOG_NAME = "tiarca-debug.1.log";
    private static final long MAX_LOG_BYTES = 512L * 1024L;
    private static final int MAX_MESSAGE_CHARS = 2000;

    private static final Pattern EMAIL = Pattern.compile(
            "\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IPV4 = Pattern.compile(
            "(?<!\\d)(?:\\d{1,3}\\.){3}\\d{1,3}(?!\\d)");
    private static final Pattern URL_USERINFO = Pattern.compile(
            "(?i)(\\b[a-z][a-z0-9+.-]*://)[^\\s/@:]+(?::[^\\s/@]*)?@");
    private static final Pattern SECRET_KV = Pattern.compile(
            "(?i)\\b(pass(?:word)?|token|secret|authorization|authpass|sasl)\\s*[:=]\\s*([^\\s,;]+)");
    private static final Pattern IRC_SECRET_COMMAND = Pattern.compile(
            "(?i)\\b(PASS|AUTHENTICATE|OPER)\\s+\\S+");
    private static final Pattern NICKSERV_IDENTIFY = Pattern.compile(
            "(?i)\\b(PRIVMSG\\s+NickServ\\s+:?IDENTIFY|IDENTIFY)\\s+\\S+");

    private static volatile Context sContext;
    private static volatile boolean sInitialized;
    private static volatile boolean sEnabled;

    private DiagnosticLog() {
    }

    public static synchronized void initialize(Context context) {
        if (context == null)
            return;
        sContext = context.getApplicationContext();
        sEnabled = preferences(sContext).getBoolean(PREF_ENABLED, false);
        sInitialized = true;
        if (sEnabled)
            writeLine("I", "APP", "Diagnostic logger resumed after process start", null);
    }

    public static boolean isEnabled(Context context) {
        ensureInitialized(context);
        return sEnabled;
    }

    public static synchronized void setEnabled(Context context, boolean enabled) {
        ensureInitialized(context);
        if (sContext == null || sEnabled == enabled)
            return;
        if (enabled) {
            preferences(sContext).edit().putBoolean(PREF_ENABLED, true).commit();
            sEnabled = true;
            clearFilesLocked();
            writeHeaderLocked();
            writeLine("I", "APP", "Diagnostic logging enabled", null);
        } else {
            writeLine("I", "APP", "Diagnostic logging disabled", null);
            preferences(sContext).edit().putBoolean(PREF_ENABLED, false).apply();
            sEnabled = false;
        }
    }

    public static void d(Context context, String category, Supplier<String> messageSupplier) {
        log(context, "D", category, messageSupplier, null);
    }

    public static void i(Context context, String category, Supplier<String> messageSupplier) {
        log(context, "I", category, messageSupplier, null);
    }

    public static void w(Context context, String category, Supplier<String> messageSupplier,
                         Throwable error) {
        log(context, "W", category, messageSupplier, error);
    }

    public static void e(Context context, String category, Supplier<String> messageSupplier,
                         Throwable error) {
        log(context, "E", category, messageSupplier, error);
    }

    private static void log(Context context, String level, String category,
                            Supplier<String> messageSupplier, Throwable error) {
        if (!isEnabled(context))
            return;
        String message;
        try {
            message = messageSupplier == null ? "" : messageSupplier.get();
        } catch (RuntimeException ignored) {
            message = "<diagnostic message unavailable>";
        }
        writeLine(level, category, message, error);
    }

    public static synchronized File createShareFile(Context context) throws IOException {
        ensureInitialized(context);
        if (sContext == null)
            throw new IOException("Diagnostic logger is not initialized");
        File cacheDir = new File(sContext.getCacheDir(), DIR_NAME);
        if (!cacheDir.exists() && !cacheDir.mkdirs())
            throw new IOException("Unable to create diagnostics cache directory");
        File output = new File(cacheDir, "TIARCA-debug-log.txt");
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
            File old = oldLogFile();
            if (old.isFile()) {
                copy(old, out);
                out.write("\n--- continued ---\n".getBytes(StandardCharsets.UTF_8));
            }
            File current = logFile();
            if (current.isFile())
                copy(current, out);
            else
                out.write("TIARCA diagnostic log is empty.\n".getBytes(StandardCharsets.UTF_8));
        }
        return output;
    }

    public static synchronized void clear(Context context) {
        ensureInitialized(context);
        if (sContext == null)
            return;
        clearFilesLocked();
        if (sEnabled) {
            writeHeaderLocked();
            writeLine("I", "APP", "Diagnostic log cleared", null);
        }
    }

    private static synchronized void writeLine(String level, String category, String message,
                                               Throwable error) {
        if (!sEnabled || sContext == null)
            return;
        try {
            File dir = diagnosticsDir();
            if (!dir.exists() && !dir.mkdirs())
                return;
            rotateIfNeededLocked();
            File file = logFile();
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                writer.print(timestamp());
                writer.print(' ');
                writer.print(level);
                writer.print('/');
                writer.print(sanitizeCategory(category));
                writer.print(' ');
                writer.println(sanitize(message));
                if (error != null)
                    writeSafeStackTrace(writer, error);
            }
        } catch (IOException ignored) {
            // Diagnostics must never affect normal app behavior.
        }
    }

    private static void writeHeaderLocked() {
        if (sContext == null)
            return;
        try {
            File dir = diagnosticsDir();
            if (!dir.exists() && !dir.mkdirs())
                return;
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(logFile(), true), StandardCharsets.UTF_8))) {
                writer.println("TIARCA diagnostic log");
                writer.println("Version: " + BuildConfig.VERSION_NAME + " (" +
                        BuildConfig.VERSION_CODE + ")");
                writer.println("Android SDK: " + Build.VERSION.SDK_INT);
                writer.println("Privacy: chat contents and credentials are intentionally excluded; " +
                        "logged values also pass through a redaction filter.");
                writer.println();
            }
        } catch (IOException ignored) {
        }
    }

    private static void writeSafeStackTrace(PrintWriter writer, Throwable error) {
        writer.println("  exception=" + error.getClass().getName());
        StackTraceElement[] stack = error.getStackTrace();
        int count = Math.min(stack.length, 12);
        for (int i = 0; i < count; i++)
            writer.println("    at " + stack[i]);
        if (stack.length > count)
            writer.println("    ... " + (stack.length - count) + " more");
    }

    static String sanitizeForTests(String value) {
        return sanitize(value);
    }

    private static String sanitize(String value) {
        if (value == null)
            return "";
        String result = value.replace('\r', ' ').replace('\n', ' ');
        result = URL_USERINFO.matcher(result).replaceAll("$1[redacted]@");
        result = NICKSERV_IDENTIFY.matcher(result).replaceAll("$1 [redacted]");
        result = IRC_SECRET_COMMAND.matcher(result).replaceAll("$1 [redacted]");
        result = SECRET_KV.matcher(result).replaceAll("$1=[redacted]");
        result = EMAIL.matcher(result).replaceAll("[email]");
        result = IPV4.matcher(result).replaceAll("[ip]");
        if (result.length() > MAX_MESSAGE_CHARS)
            result = result.substring(0, MAX_MESSAGE_CHARS) + "…";
        return result;
    }

    private static String sanitizeCategory(String value) {
        if (value == null || value.trim().isEmpty())
            return "GENERAL";
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static void ensureInitialized(Context context) {
        if (sInitialized)
            return;
        initialize(context);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static File diagnosticsDir() {
        return new File(sContext.getFilesDir(), DIR_NAME);
    }

    private static File logFile() {
        return new File(diagnosticsDir(), LOG_NAME);
    }

    private static File oldLogFile() {
        return new File(diagnosticsDir(), OLD_LOG_NAME);
    }

    private static void rotateIfNeededLocked() {
        File current = logFile();
        if (!current.isFile() || current.length() < MAX_LOG_BYTES)
            return;
        File old = oldLogFile();
        if (old.exists())
            old.delete();
        current.renameTo(old);
        writeHeaderLocked();
    }

    private static void clearFilesLocked() {
        File current = logFile();
        File old = oldLogFile();
        if (current.exists())
            current.delete();
        if (old.exists())
            old.delete();
    }

    private static void copy(File source, BufferedOutputStream output) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source))) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1)
                output.write(buffer, 0, count);
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    static synchronized void resetForTests() {
        sContext = null;
        sInitialized = false;
        sEnabled = false;
    }
}
