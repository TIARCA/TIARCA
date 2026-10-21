package io.mrarm.chatlib.android.storage;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import io.mrarm.chatlib.dto.MessageInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SQLiteMessageStorageNickRenameTest {

    private SQLiteMessageStorageApi storage;
    private SQLiteMessageStorageFile file;
    private File directory;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        directory = new File(context.getCacheDir(), "nick-history-" + UUID.randomUUID());
        assertTrue(directory.mkdirs());
        storage = new SQLiteMessageStorageApi(directory);
        file = new SQLiteMessageStorageFile(storage, 1L,
                new File(directory, "messages-test.db"), false);
    }

    @After
    public void tearDown() {
        if (file != null)
            file.close(false);
        if (storage != null)
            storage.close();
        if (directory != null && directory.isDirectory()) {
            File[] children = directory.listFiles();
            if (children != null)
                for (File child : children)
                    assertTrue(child.delete());
            assertTrue(directory.delete());
        }
    }

    @Test
    public void renameFindsHistoricalNicknameDespiteDifferentCapitalization() {
        add("ReSiLiEnZa", "original history");
        file.renameChannel("resilienza", "RESILIENZA_OUT");

        assertEquals(1, messages("resilienza_out").size());
        assertTrue(messages("RESILIENZA_OUT").contains("original history"));
    }

    @Test
    public void renameMergesExistingDestinationWithoutDuplication() {
        add("Resilienza", "previous message");
        add("resilienza_out", "new message");

        file.renameChannel("RESILIENZA", "RESILIENZA_OUT");
        file.renameChannel("RESILIENZA", "RESILIENZA_OUT");

        List<String> merged = messages("resilienza_out");
        assertEquals(2, merged.size());
        assertTrue(merged.contains("previous message"));
        assertTrue(merged.contains("new message"));
    }

    @Test
    public void consecutiveNickChangesPreserveHistory() {
        add("Resilienza", "original");
        file.renameChannel("RESILIENZA", "Resilienza_out");
        add("Resilienza_out", "second");
        file.renameChannel("RESILIENZA_OUT", "another_nick");

        List<String> merged = messages("another_nick");
        assertEquals(2, merged.size());
        assertTrue(merged.contains("original"));
        assertTrue(merged.contains("second"));
    }

    private void add(String nick, String text) {
        file.addMessage(nick, new MessageInfo(null, new Date(1_760_000_000_000L),
                text, MessageInfo.MessageType.NORMAL));
    }

    private List<String> messages(String nick) {
        MessageQueryResult result = file.getMessages(nick, -1, 0, 100, false, null);
        return result.getMessages().stream().map(MessageInfo::getMessage)
                .collect(Collectors.toList());
    }
}
