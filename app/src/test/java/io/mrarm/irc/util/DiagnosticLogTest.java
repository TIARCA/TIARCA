package io.mrarm.irc.util;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class DiagnosticLogTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("tiarca_diagnostics", Context.MODE_PRIVATE)
                .edit().clear().commit();
        deleteRecursively(new File(context.getFilesDir(), "diagnostics"));
        deleteRecursively(new File(context.getCacheDir(), "diagnostics"));
        DiagnosticLog.resetForTests();
    }

    @After
    public void tearDown() {
        DiagnosticLog.setEnabled(context, false);
        context.getSharedPreferences("tiarca_diagnostics", Context.MODE_PRIVATE)
                .edit().clear().commit();
        DiagnosticLog.resetForTests();
    }

    @Test
    public void disabledLoggerDoesNotEvaluateMessageSupplier() {
        AtomicBoolean evaluated = new AtomicBoolean(false);

        DiagnosticLog.i(context, "TEST", () -> {
            evaluated.set(true);
            return "expensive diagnostic";
        });

        assertFalse(evaluated.get());
    }

    @Test
    public void sanitizerRedactsCommonCredentialsAndIdentifiers() {
        String input = "PASS hunter2 token=abcdef user@example.com 192.168.1.23 " +
                "https://marco:secret@example.org/path PRIVMSG NickServ :IDENTIFY password123";
        String sanitized = DiagnosticLog.sanitizeForTests(input);

        assertFalse(sanitized.contains("hunter2"));
        assertFalse(sanitized.contains("abcdef"));
        assertFalse(sanitized.contains("user@example.com"));
        assertFalse(sanitized.contains("192.168.1.23"));
        assertFalse(sanitized.contains("marco:secret"));
        assertFalse(sanitized.contains("password123"));
        assertTrue(sanitized.contains("[redacted]"));
        assertTrue(sanitized.contains("[email]"));
        assertTrue(sanitized.contains("[ip]"));
    }

    @Test
    public void enabledLoggerWritesOnlySanitizedShareContent() throws Exception {
        DiagnosticLog.setEnabled(context, true);
        DiagnosticLog.i(context, "AUTH", () ->
                "password=mySecret token=abc123 contact=user@example.com host=10.0.0.8");

        File shared = DiagnosticLog.createShareFile(context);
        String content = new String(Files.readAllBytes(shared.toPath()), StandardCharsets.UTF_8);

        assertTrue(content.contains("TIARCA diagnostic log"));
        assertTrue(content.contains("Android:"));
        assertTrue(content.contains("Device:"));
        assertTrue(content.contains("Locale:"));
        assertTrue(content.contains("Timezone:"));
        assertTrue(content.contains("AUTH"));
        assertTrue(content.contains("["));
        assertFalse(content.contains("mySecret"));
        assertFalse(content.contains("abc123"));
        assertFalse(content.contains("user@example.com"));
        assertFalse(content.contains("10.0.0.8"));
    }

    @Test
    public void contextFreeLoggerWorksAfterInitialization() throws Exception {
        DiagnosticLog.setEnabled(context, true);

        DiagnosticLog.i("DCC", () -> "context-free diagnostic");

        File shared = DiagnosticLog.createShareFile(context);
        String content = new String(Files.readAllBytes(shared.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("I/DCC"));
        assertTrue(content.contains("context-free diagnostic"));
    }

    @Test
    public void contextFreeLoggerDoesNothingBeforeInitialization() {
        AtomicBoolean evaluated = new AtomicBoolean(false);

        DiagnosticLog.i("TEST", () -> {
            evaluated.set(true);
            return "should not run";
        });

        assertFalse(evaluated.get());
    }

    @Test
    public void pseudonymsAreStableWithoutExposingSourceValue() {
        String one = DiagnosticLog.pseudonym("server", "11111111-2222-3333-4444-555555555555");
        String same = DiagnosticLog.pseudonym("server", "11111111-2222-3333-4444-555555555555");
        String other = DiagnosticLog.pseudonym("server", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        assertEquals(one, same);
        assertNotEquals(one, other);
        assertTrue(one.startsWith("server-"));
        assertFalse(one.contains("11111111"));
    }

    private static void deleteRecursively(File file) {
        if (!file.exists())
            return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children)
                    deleteRecursively(child);
            }
        }
        file.delete();
    }
}
