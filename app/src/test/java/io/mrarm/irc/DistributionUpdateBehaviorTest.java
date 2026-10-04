package io.mrarm.irc;

import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class DistributionUpdateBehaviorTest {
    @Test
    public void aboutDialogUsesTheDistributionUpdateAction() {
        try (ActivityController<FragmentActivity> controller = Robolectric.buildActivity(FragmentActivity.class)) {
            FragmentActivity activity = controller.get();
            activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat);
            controller.setup();
            UpdateManager.showAboutDialog(activity);
            AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            View content = dialog.getWindow().getDecorView();
            assertEquals(BuildConfig.GITHUB_UPDATER_ENABLED ? 1 : 0, countCheckboxes(content));
            if (!BuildConfig.GITHUB_UPDATER_ENABLED) {
                Button open = findButton(content, "Open in F-Droid", "Apri in F-Droid");
                assertNotNull(open);
                open.performClick();
                Intent intent = shadowOf(activity).getNextStartedActivity();
                assertEquals(Intent.ACTION_VIEW, intent.getAction());
                assertEquals("https://f-droid.org/packages/io.tiarca.irc/", intent.getDataString());
                assertFalse(activity.getSharedPreferences("tiarca_updater", 0).contains("last_attempt"));
            }
            dialog.dismiss();
        }
    }

    @Test
    public void fdroidIgnoresAutomaticUpdaterPreferences() {
        if (BuildConfig.GITHUB_UPDATER_ENABLED) return;
        try (ActivityController<FragmentActivity> controller = Robolectric.buildActivity(FragmentActivity.class).setup()) {
            FragmentActivity activity = controller.get();
            activity.getSharedPreferences("tiarca_updater", 0).edit()
                    .putBoolean("choice_made", true).putBoolean("automatic", true).commit();
            UpdateManager.maybePromptAndCheck(activity);
            UpdateManager.checkForUpdates(activity, false);
            assertFalse(activity.getSharedPreferences("tiarca_updater", 0).contains("last_attempt"));
        }
    }

    private static int countCheckboxes(View view) {
        int count = view instanceof CheckBox ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += countCheckboxes(group.getChildAt(i));
        }
        return count;
    }

    private static Button findButton(View view, String english, String italian) {
        if (view instanceof Button && (english.contentEquals(((Button) view).getText())
                || italian.contentEquals(((Button) view).getText()))) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), english, italian);
                if (found != null) return found;
            }
        }
        return null;
    }
}
