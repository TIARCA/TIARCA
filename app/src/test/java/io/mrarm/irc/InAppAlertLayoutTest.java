package io.mrarm.irc;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Regression test for alerts hidden behind the elevated chat content frame. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class InAppAlertLayoutTest {

    @Test
    public void alertContainerIsAboveTheChatContent() {
        Context context = new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.AppTheme_NoActionBar);
        View root = LayoutInflater.from(context).inflate(R.layout.activity_main, null, false);
        View content = root.findViewById(R.id.content_frame);
        View alertHolder = root.findViewById(R.id.in_app_alert_holder);
        assertNotNull(content);
        assertNotNull(alertHolder);
        assertTrue("A child card elevation cannot raise a lower-elevation parent",
                alertHolder.getElevation() > content.getElevation());
    }
}
