package io.mrarm.irc.lua;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LuaModerationCollectorTest {
    private static final String A = "11111111-1111-1111-1111-111111111111";
    private static final String B = "22222222-2222-2222-2222-222222222222";
    private LuaModerationCollector collector;

    @Before public void setup() {
        Context app = ApplicationProvider.getApplicationContext();
        collector = new LuaModerationCollector(app);
        collector.setEnabled(A, false);
        collector.setEnabled(B, false);
        new File(new File(app.getFilesDir(), "moderation-collector"), A + ".jsonl").delete();
        new File(new File(app.getFilesDir(), "moderation-collector"), B + ".jsonl").delete();
    }

    private void write(String server, String event, String detail) throws Exception {
        collector.append(server, event, "#amicizia", "Operatore", "Nick",
                detail, 1795070000L, "Ciao\nmondo", 1795069998L,
                "observed_irc", "ident", "host.example", "account");
    }

    @Test public void disabledCollectorDoesNotCreateArchive() throws Exception {
        write(A, "KICK", "spam");
        assertEquals(0, collector.size(A));
    }

    @Test public void jsonlAppendPreservesAllEntriesAndSeparatesNetworks() throws Exception {
        assertTrue(collector.setEnabled(A, true));
        assertTrue(collector.setEnabled(B, true));
        for (int i = 0; i < 40; i++) write(A, "KICK", "reason " + i);
        write(B, "BAN", "MODE +b");
        File file = collector.snapshot(A);
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        assertEquals(40, lines.size()); // Never overwrites at 32 records.
        JSONObject first = new JSONObject(lines.get(0));
        JSONObject last = new JSONObject(lines.get(39));
        assertEquals("reason 0", first.getString("detail"));
        assertEquals("reason 39", last.getString("detail"));
        assertEquals("Ciao\nmondo", last.getString("prior_message"));
        assertEquals("host.example", last.getString("host"));
        assertFalse(last.getBoolean("prior_message_is_proven_cause"));
        assertEquals(1, Files.readAllLines(collector.snapshot(B).toPath()).size());
    }

    @Test public void disablingAndReenablingDoesNotEraseExistingRecords() throws Exception {
        collector.setEnabled(A, true);
        write(A, "KICK", "first");
        collector.setEnabled(A, false);
        write(A, "KICK", "not saved");
        collector = new LuaModerationCollector(ApplicationProvider.getApplicationContext());
        assertFalse(collector.enabled(A));
        collector.setEnabled(A, true);
        write(A, "BAN", "second");
        assertEquals(2, Files.readAllLines(collector.snapshot(A).toPath()).size());
    }

    @Test public void automodRecordsTriggerNotInferredCause() throws Exception {
        collector.setEnabled(A, true);
        collector.append(A, "AUTOMOD", "#amicizia", "automod_antiripetizione.lua",
                "Nick", "AutoMod 11111111/#amicizia Nick 75.0% infrazione 2 (kick)",
                1795070000L, "Messaggio contestato", 1795069999L,
                "automod_decision_unconfirmed", null, null, null);
        JSONObject row = new JSONObject(Files.readAllLines(collector.snapshot(A).toPath()).get(0));
        assertEquals("Messaggio contestato", row.getString("trigger_message"));
        assertTrue(row.isNull("prior_message"));
        assertEquals("kick", row.getString("requested_action"));
        assertEquals(75.0, row.getDouble("similarity_percent"), 0.001);
        assertEquals(2, row.getInt("infraction_level"));
        assertTrue(row.isNull("account"));
    }
}
