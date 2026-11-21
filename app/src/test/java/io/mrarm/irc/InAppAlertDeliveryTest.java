package io.mrarm.irc;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.mrarm.irc.config.InAppAlertSettings;
import io.mrarm.irc.config.SettingsHelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class InAppAlertDeliveryTest {
    private static final UUID SERVER = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");

    private NotificationManager manager;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        SettingsHelper.getInstance(ApplicationProvider.getApplicationContext());
        preferences = SettingsHelper.getPreferences();
        preferences.edit().putBoolean(InAppAlertSettings.PREF_ENABLED, true)
                .putBoolean(InAppAlertSettings.PREF_PRIVATE_MESSAGES, true)
                .putBoolean(InAppAlertSettings.PREF_MENTIONS, true).commit();
        manager = new NotificationManager();
    }

    @After
    public void tearDown() {
        manager.setInAppAlertListener(null);
        preferences.edit().remove(InAppAlertSettings.PREF_ENABLED)
                .remove(InAppAlertSettings.PREF_PRIVATE_MESSAGES)
                .remove(InAppAlertSettings.PREF_MENTIONS).commit();
    }

    private static InAppAlertQueue.Alert privateMessage(String nick) {
        return new InAppAlertQueue.Alert(SERVER, nick, nick, "Hello",
                true, "msg-" + nick, false);
    }

    private static InAppAlertQueue.Alert mention() {
        return new InAppAlertQueue.Alert(SERVER, "#test", "alice", "Hello nick",
                false, "msg-mention", true);
    }

    private static void flushMainThread() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static final class Listener implements NotificationManager.InAppAlertListener {
        boolean conversationVisible;
        final List<Boolean> callbacks = new ArrayList<>();

        @Override
        public void onInAppAlertsChanged(boolean newlyReceived) {
            callbacks.add(newlyReceived);
        }

        @Override
        public boolean isConversationVisible(ServerConnectionInfo connection, String target) {
            return conversationVisible;
        }
    }

    @Test
    public void backgroundPrivateAndMentionAlertsAppearInOrderAfterResumeWithoutSound() {
        InAppAlertQueue.Alert first = privateMessage("alice");
        InAppAlertQueue.Alert second = mention();
        manager.enqueueInAppAlert(null, first);
        manager.enqueueInAppAlert(null, second);
        flushMainThread();

        assertSame(first, manager.getCurrentInAppAlert());
        assertEquals(1, manager.getWaitingInAppAlertCount());

        Listener resumed = new Listener();
        resumed.conversationVisible = true; // The same chat may be selected on resume.
        manager.setInAppAlertListener(resumed);
        assertEquals(1, resumed.callbacks.size());
        assertFalse(resumed.callbacks.get(0));

        manager.dismissCurrentInAppAlert();
        assertSame(second, manager.getCurrentInAppAlert());
        assertEquals(0, manager.getWaitingInAppAlertCount());
    }

    @Test
    public void backgroundArrivalProcessedAfterResumeDoesNotDisappearIntoVisibleChat() {
        InAppAlertQueue.Alert alert = privateMessage("alice");
        manager.enqueueInAppAlert(null, alert);

        Listener resumed = new Listener();
        resumed.conversationVisible = true;
        manager.setInAppAlertListener(resumed);
        flushMainThread();

        assertSame(alert, manager.getCurrentInAppAlert());
        assertEquals(2, resumed.callbacks.size());
        assertFalse(resumed.callbacks.get(1)); // No delayed notification sound.
    }

    @Test
    public void visibleConversationIsSuppressedOnlyWhileActivityRemainsResumed() {
        Listener active = new Listener();
        active.conversationVisible = true;
        manager.setInAppAlertListener(active);
        active.callbacks.clear();

        manager.enqueueInAppAlert(null, privateMessage("visible"));
        flushMainThread();
        assertNull(manager.getCurrentInAppAlert());
        assertTrue(active.callbacks.isEmpty());

        InAppAlertQueue.Alert background = privateMessage("after-pause");
        manager.enqueueInAppAlert(null, background);
        manager.setInAppAlertListener(null);
        flushMainThread();
        assertSame(background, manager.getCurrentInAppAlert());

        manager.setInAppAlertListener(active);
        assertEquals(1, active.callbacks.size());
        assertFalse(active.callbacks.get(0));
    }

    @Test
    public void notificationFiltersStillApplyToBackgroundAndPendingAlerts() {
        preferences.edit().putBoolean(InAppAlertSettings.PREF_MENTIONS, false).commit();
        manager.enqueueInAppAlert(null, mention());
        InAppAlertQueue.Alert direct = privateMessage("alice");
        manager.enqueueInAppAlert(null, direct);
        flushMainThread();
        assertSame(direct, manager.getCurrentInAppAlert());

        preferences.edit().putBoolean(InAppAlertSettings.PREF_PRIVATE_MESSAGES, false).commit();
        Listener resumed = new Listener();
        manager.setInAppAlertListener(resumed);
        assertNull(manager.getCurrentInAppAlert());
    }

    @Test
    public void foregroundMentionInAnotherChannelIsDisplayedWithoutAffectingCurrentChat() {
        Listener active = new Listener();
        active.conversationVisible = false; // Reading #chatitaly, TAB received on #amicizia.
        manager.setInAppAlertListener(active);
        active.callbacks.clear();

        InAppAlertQueue.Alert otherChannel = mention();
        manager.enqueueInAppAlert(null, otherChannel);
        flushMainThread();
        assertSame(otherChannel, manager.getCurrentInAppAlert());
        assertEquals(1, active.callbacks.size());
        assertTrue(active.callbacks.get(0));

        manager.dismissCurrentInAppAlert();
        assertNull(manager.getCurrentInAppAlert());
        active.conversationVisible = true; // Reading the channel that receives the TAB.
        active.callbacks.clear();

        manager.enqueueInAppAlert(null, mention());
        flushMainThread();
        assertNull(manager.getCurrentInAppAlert());
        assertTrue(active.callbacks.isEmpty());
    }

    @Test
    public void backgroundCallerIdRequestsAreRetained() {
        InAppAlertQueue.Alert request = InAppAlertQueue.Alert.callerId(SERVER, "alice");
        manager.enqueueInAppAlert(null, request);
        flushMainThread();

        Listener resumed = new Listener();
        resumed.conversationVisible = true;
        manager.setInAppAlertListener(resumed);
        assertSame(request, manager.getCurrentInAppAlert());
        assertFalse(resumed.callbacks.get(0));
    }
}
