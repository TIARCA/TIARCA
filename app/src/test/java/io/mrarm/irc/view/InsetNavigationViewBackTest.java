package io.mrarm.irc.view;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.ContextThemeWrapper;

import androidx.activity.BackEventCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class InsetNavigationViewBackTest {

    @Test
    public void backWithVisibleKeyboardIsConsumedByMembersDrawer() {
        TestNavigationView view = newTestView();
        view.imeVisible = true;

        // Without our override, NavigationView.handleBackInvoked() would attempt to
        // close its parent DrawerLayout, rather than dismissing the keyboard.
        view.handleBackInvoked();

        assertEquals(1, view.hideImeCalls);
    }

    @Test
    public void predictiveBackPreservesKeyboardVisibilityFromGestureStart() {
        TestNavigationView view = newTestView();
        view.imeVisible = true;
        view.startBackProgress(backEvent(0.1f));

        // Insets may change before the gesture is committed.
        view.imeVisible = false;
        view.updateBackProgress(backEvent(0.8f));
        view.handleBackInvoked();

        assertEquals(1, view.hideImeCalls);
    }

    private static BackEventCompat backEvent(float progress) {
        return new BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT);
    }

    private static TestNavigationView newTestView() {
        Context context = new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.AppTheme);
        return new TestNavigationView(context);
    }

    private static class TestNavigationView extends InsetNavigationView {
        boolean imeVisible;
        int hideImeCalls;

        TestNavigationView(Context context) {
            super(context);
        }

        @Override
        protected boolean isImeVisible() {
            return imeVisible;
        }

        @Override
        protected void hideIme() {
            hideImeCalls++;
        }
    }
}
