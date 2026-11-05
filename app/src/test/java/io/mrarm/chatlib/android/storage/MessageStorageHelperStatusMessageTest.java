package io.mrarm.chatlib.android.storage;

import java.util.Date;
import org.junit.Test;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import static org.junit.Assert.*;

public class MessageStorageHelperStatusMessageTest {

    @Test
    public void storesStatusFlagSeparatelyFromOriginalText() {
        MessageSenderInfo sender = new MessageSenderInfo("Alice", null, null, null, null);
        MessageInfo.Builder builder = new MessageInfo.Builder(sender, "hello ops",
                MessageInfo.MessageType.NORMAL);
        builder.setStatusMessage(true);
        MessageInfo original = builder.build();
        String extra = MessageStorageHelper.serializeExtraData(original);
        MessageInfo restored = MessageStorageHelper.deserializeMessage(
                sender, new Date(), original.getMessage(),
                MessageInfo.MessageType.NORMAL.asInt(), extra);
        assertTrue(restored.isStatusMessage());
        assertEquals("hello ops", restored.getMessage());
    }

    @Test
    public void olderMessagesWithoutStatusFlagRemainUnmarked() {
        MessageInfo restored = MessageStorageHelper.deserializeMessage(
                new MessageSenderInfo("Alice", null, null, null, null),
                new Date(), "hello", MessageInfo.MessageType.NORMAL.asInt(), "{}");
        assertFalse(restored.isStatusMessage());
        assertEquals("hello", restored.getMessage());
    }
}
