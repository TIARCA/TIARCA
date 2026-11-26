package io.mrarm.irc.config;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class SettingsHelperListenerTest {
    private static final String KEY = "listener_regression_test";

    @Test public void callbackRunsOnceAndCancellationIsScopedToHandle() {
        SettingsHelper settings = new SettingsHelper(RuntimeEnvironment.getApplication());
        int[] calls = {0};
        SettingsHelper.SettingChangeCallback callback = key -> calls[0]++;
        SettingsHelper.ListenerHandle one = SettingsHelper.changeEvent().listen(KEY, callback);
        SettingsHelper.ListenerHandle two = SettingsHelper.changeEvent().listen(KEY, callback);
        try {
            settings.onSharedPreferenceChanged(null, KEY);
            assertEquals(2, calls[0]);
            one.cancel();
            settings.onSharedPreferenceChanged(null, KEY);
            assertEquals(3, calls[0]);
            two.cancel();
            settings.onSharedPreferenceChanged(null, KEY);
            assertEquals(3, calls[0]);
        } finally {
            one.cancel();
            two.cancel();
        }
    }

    static final class Owner {
        int calls;
        @SettingChangeCallback(keys = {KEY})
        private void changed() { calls++; }
    }

    @Test public void annotationRegistrationIsIdempotentAndRemovable() {
        SettingsHelper settings = new SettingsHelper(RuntimeEnvironment.getApplication());
        Owner owner = new Owner();
        try {
            SettingsHelper.registerCallbacks(owner);
            SettingsHelper.registerCallbacks(owner);
            settings.onSharedPreferenceChanged(null, KEY);
            assertEquals(1, owner.calls);
            SettingsHelper.unregisterCallbacks(owner);
            settings.onSharedPreferenceChanged(null, KEY);
            assertEquals(1, owner.calls);
            SettingsHelper.registerCallbacks(owner);
            settings.onSharedPreferenceChanged(null, KEY);
            assertEquals(2, owner.calls);
        } finally {
            SettingsHelper.unregisterCallbacks(owner);
        }
    }

    @Test public void listenerMayUnregisterItselfDuringDelivery() {
        SettingsHelper settings = new SettingsHelper(RuntimeEnvironment.getApplication());
        SettingsHelper.ListenerHandle handle = SettingsHelper.changeEvent();
        int[] calls = {0};
        handle.listen(KEY, key -> { calls[0]++; handle.cancel(); });
        try {
            settings.onSharedPreferenceChanged(null, KEY);
            settings.onSharedPreferenceChanged(null, KEY);
            assertEquals(1, calls[0]);
        } finally {
            handle.cancel();
        }
    }
}
