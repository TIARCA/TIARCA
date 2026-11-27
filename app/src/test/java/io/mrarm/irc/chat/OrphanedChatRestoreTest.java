package io.mrarm.irc.chat;

import android.app.Application;
import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.UUID;

import io.mrarm.irc.ServerConnectionManager;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class OrphanedChatRestoreTest {

    @Test
    public void missingServerChannelPageSurvivesRestorationAndRemoval() {
        verifyOrphanedPage(false);
    }

    @Test
    public void missingServerStatusPageSurvivesRestorationAndRemoval() {
        verifyOrphanedPage(true);
    }

    @Test
    public void missingOrInvalidSavedUuidDoesNotCreateConnection() {
        try (ActivityController<FragmentActivity> controller =
                     Robolectric.buildActivity(FragmentActivity.class).setup()) {
            ServerConnectionManager manager = ServerConnectionManager.getInstance(controller.get());
            assertNull(manager.getConnectionForSavedState(null));
            assertNull(manager.getConnectionForSavedState("invalid-uuid"));
            assertNull(manager.getConnectionForSavedState(UUID.randomUUID().toString()));
        }
    }

    private void verifyOrphanedPage(boolean status) {
        try (ActivityController<FragmentActivity> controller =
                     Robolectric.buildActivity(FragmentActivity.class).setup()) {
            ChatMessagesFragment page = new ChatMessagesFragment();
            Bundle args = new Bundle();
            args.putString(ChatFragment.ARG_SERVER_UUID, UUID.randomUUID().toString());
            if (status)
                args.putBoolean("display_status", true);
            else
                args.putString(ChatFragment.ARG_CHANNEL_NAME, "#test");
            page.setArguments(args);

            controller.get().getSupportFragmentManager().beginTransaction()
                    .add(page, "orphan").commitNow();
            assertNull(page.getConnectionInfo());
            assertTrue(page.requireView() instanceof FrameLayout);

            controller.recreate();
            ChatMessagesFragment restored = (ChatMessagesFragment) controller.get()
                    .getSupportFragmentManager().findFragmentByTag("orphan");
            assertNotNull(restored);
            assertNull(restored.getConnectionInfo());
            assertTrue(restored.requireView() instanceof FrameLayout);
            controller.get().getSupportFragmentManager().beginTransaction()
                    .remove(restored).commitNow();
        }
    }
}
