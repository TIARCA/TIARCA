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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LuaScriptJournalTest {
    private static final String SCRIPT = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER = "33333333-3333-3333-3333-333333333333";
    private static final String A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private LuaScriptJournal journal;

    @Before public void setup() {
        Context app = ApplicationProvider.getApplicationContext();
        journal = new LuaScriptJournal(app);
        for (String s : new String[]{SCRIPT, OTHER}) {
            for (String network : new String[]{A, B})
                new File(new File(new File(app.getFilesDir(), "lua_journals"), s),
                        network + ".jsonl").delete();
            File archives = new File(new File(new File(app.getFilesDir(), "lua_journals"), s),
                    "archives");
            File[] old = archives.listFiles();
            if (old != null) for (File entry : old) entry.delete();
        }
    }

    @Test public void fourHundredRecordsRemainIncrementalAndIsolated() throws Exception {
        for (int i = 0; i < 400; i++)
            journal.append(SCRIPT, A,
                    new JSONObject().put("action", "kick")
                            .put("sequence", i)
                            .put("message", "ciao\n" + i).toString());
        journal.append(SCRIPT, B, "{\"event\":\"ban\"}");
        journal.append(OTHER, A, "{\"event\":\"automod\"}");
        List<String> lines = Files.readAllLines(journal.snapshot(SCRIPT, A).toPath(),
                StandardCharsets.UTF_8);
        assertEquals(400, lines.size());
        assertEquals(0, new JSONObject(lines.get(0)).getInt("sequence"));
        assertEquals(399, new JSONObject(lines.get(399)).getInt("sequence"));
        assertEquals("ciao\n399", new JSONObject(lines.get(399)).getString("message"));
        assertEquals(1, Files.readAllLines(journal.snapshot(SCRIPT, B).toPath()).size());
        assertEquals(1, Files.readAllLines(journal.snapshot(OTHER, A).toPath()).size());
        assertTrue(journal.size(SCRIPT, A) > 8192); // No 128-KiB shared state quota.
    }

    @Test public void journalSurvivesNewInstanceAndDoesNotOverwrite() throws Exception {
        journal.append(SCRIPT, A, "{\"event\":\"first\"}");
        journal = new LuaScriptJournal(ApplicationProvider.getApplicationContext());
        journal.append(SCRIPT, A, "{\"event\":\"second\"}");
        List<String> lines = Files.readAllLines(journal.snapshot(SCRIPT, A).toPath());
        assertEquals(2, lines.size());
        assertEquals("first", new JSONObject(lines.get(0)).getString("event"));
        assertEquals("second", new JSONObject(lines.get(1)).getString("event"));
    }

    @Test public void rotationKeepsRecoverableArchiveAndStartsFreshJournal() throws Exception {
        journal.append(SCRIPT, A, "{\"event\":\"before\"}");
        journal.append(SCRIPT, A, "{\"event\":\"older\"}");
        journal.append(SCRIPT, B, "{\"event\":\"unrelated-network\"}");

        File exported = journal.rotate(SCRIPT, A);
        assertEquals(0, journal.size(SCRIPT, A));
        assertEquals(2, Files.readAllLines(exported.toPath()).size());
        File recovered = journal.snapshotLastArchive(SCRIPT, A);
        assertEquals(2, Files.readAllLines(recovered.toPath()).size());

        journal.append(SCRIPT, A, "{\"event\":\"after\"}");
        assertEquals(1, Files.readAllLines(journal.snapshot(SCRIPT, A).toPath()).size());
        assertEquals("before", new JSONObject(
                Files.readAllLines(journal.snapshotLastArchive(SCRIPT, A).toPath())
                        .get(0)).getString("event"));
        assertEquals("unrelated-network", new JSONObject(
                Files.readAllLines(journal.snapshot(SCRIPT, B).toPath())
                        .get(0)).getString("event"));

        journal = new LuaScriptJournal(ApplicationProvider.getApplicationContext());
        assertEquals(2, Files.readAllLines(
                journal.snapshotLastArchive(SCRIPT, A).toPath()).size());
        File latest = journal.rotate(SCRIPT, A);
        assertEquals("after", new JSONObject(
                Files.readAllLines(latest.toPath()).get(0)).getString("event"));
        assertEquals(0, journal.size(SCRIPT, A));
    }

    @Test public void rotationRejectsEmptyJournalWithoutErasingArchives() throws Exception {
        journal.append(SCRIPT, A, "{\"event\":\"keep\"}");
        journal.rotate(SCRIPT, A);
        try {
            journal.rotate(SCRIPT, A);
            fail("Rotated an empty journal");
        } catch (IOException expected) { }
        assertEquals("keep", new JSONObject(
                Files.readAllLines(journal.snapshotLastArchive(SCRIPT, A).toPath())
                        .get(0)).getString("event"));
    }

    @Test public void rejectsNonJsonMultilineOversizeAndPathTraversal() throws Exception {
        for (String invalid : new String[]{"[1,2]", "not json", "{\"x\":1}\n{\"x\":2}",
                "{\"big\":\"" + "x".repeat(8192) + "\"}"}) {
            try {
                journal.append(SCRIPT, A, invalid);
                fail("Accepted invalid JSONL");
            } catch (IOException expected) { }
        }
        try {
            journal.append("../bad", A, "{\"ok\":true}");
            fail("Accepted invalid script path");
        } catch (IllegalArgumentException expected) { }
        assertEquals(0, journal.size(SCRIPT, A));
    }
}
