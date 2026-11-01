package io.mrarm.chatlib.android.storage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import java.util.Date;
import java.util.UUID;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class SQLitePrivatePresenceTest {
    @Test public void quitAndMonitorWithoutUuidSurviveSqliteRoundTrip() {
        File dir = new File(ApplicationProvider.getApplicationContext().getCacheDir(),
                "pvt-test-" + UUID.randomUUID());
        assertTrue(dir.mkdirs());
        SQLiteMessageStorageApi api = new SQLiteMessageStorageApi(dir);
        SQLiteMessageStorageFile file = new SQLiteMessageStorageFile(api, 1L,
                new File(dir, "messages-test.db"), false);
        try {
            MessageSenderInfo sender = new MessageSenderInfo("Alice", "ident", "host", null, null);
            file.addMessage("Alice", new MessageInfo(sender, new Date(),
                    "Ping timeout", MessageInfo.MessageType.QUIT));
            file.addMessage("Alice", new MessageInfo(sender, new Date(), null,
                    MessageInfo.MessageType.PRIVATE_PRESENCE_OFFLINE));
            java.util.List<MessageInfo> messages = file.getMessages(
                    "Alice", -1, 0, 20, false, null).getMessages();
            assertEquals(2, messages.size());
            assertEquals("Alice", messages.get(0).getSender().getNick());
            assertEquals("Ping timeout", messages.get(0).getMessage());
            assertNull(messages.get(0).getSender().getUserUUID());
            assertEquals(MessageInfo.MessageType.PRIVATE_PRESENCE_OFFLINE,
                    messages.get(1).getType());
            assertEquals("Alice", messages.get(1).getSender().getNick());
        } finally {
            file.close(false);
            api.close();
        }
    }
}
