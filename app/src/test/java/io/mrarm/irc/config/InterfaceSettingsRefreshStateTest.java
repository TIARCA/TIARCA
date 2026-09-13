package io.mrarm.irc.config;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InterfaceSettingsRefreshStateTest {

    @Test
    public void pendingRefreshIsConsumedOnlyOnce() {
        assertFalse(InterfaceSettingsRefreshState.consumeRefreshPending());

        InterfaceSettingsRefreshState.markRefreshPending();

        assertTrue(InterfaceSettingsRefreshState.consumeRefreshPending());
        assertFalse(InterfaceSettingsRefreshState.consumeRefreshPending());
    }

    @Test
    public void repeatedVisualChangesStillProduceOnePendingRefresh() {
        InterfaceSettingsRefreshState.markRefreshPending();
        InterfaceSettingsRefreshState.markRefreshPending();
        InterfaceSettingsRefreshState.markRefreshPending();

        assertTrue(InterfaceSettingsRefreshState.consumeRefreshPending());
        assertFalse(InterfaceSettingsRefreshState.consumeRefreshPending());
    }

    @Test
    public void fontAndMessageAppearancePreferencesRequireRefresh() {
        assertTrue(InterfaceSettingsRefreshState.isRefreshRelevantPreference(
                ChatSettings.PREF_FONT));
        assertTrue(InterfaceSettingsRefreshState.isRefreshRelevantPreference(
                ChatSettings.PREF_FONT_SIZE));
        assertTrue(InterfaceSettingsRefreshState.isRefreshRelevantPreference(
                MessageFormatSettings.PREF_MESSAGE_FORMAT));
        assertTrue(InterfaceSettingsRefreshState.isRefreshRelevantPreference(
                MessageFormatSettings.PREF_MESSAGE_AVATARS));
        assertTrue(InterfaceSettingsRefreshState.isRefreshRelevantPreference(
                RightClockSettings.PREF_MESSAGE_TIME_RIGHT));
        assertFalse(InterfaceSettingsRefreshState.isRefreshRelevantPreference(
                "unrelated_setting"));
    }
}
