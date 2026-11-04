package io.mrarm.irc.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SwipeDismissCardViewTest {

    @Test
    public void dismissalThresholdWorksInBothDirections() {
        assertTrue(SwipeDismissCardView.isPastDismissThreshold(-120, 400));
        assertTrue(SwipeDismissCardView.isPastDismissThreshold(120, 400));
        assertFalse(SwipeDismissCardView.isPastDismissThreshold(90, 400));
        assertFalse(SwipeDismissCardView.isPastDismissThreshold(120, 0));
    }

    @Test
    public void swipeDismissesButSmallGestureDoesNot() {
        Context context = ApplicationProvider.getApplicationContext();
        SwipeDismissCardView card = new SwipeDismissCardView(context);
        card.layout(0, 0, 400, 80);
        AtomicInteger dismissed = new AtomicInteger();
        card.setOnSwipeListener(new SwipeDismissCardView.OnSwipeListener() {
            @Override public void onSwipeProgress(float distance) { }
            @Override public void onSwipeDismissed() { dismissed.incrementAndGet(); }
        });

        swipe(card, 200, 50);
        assertEquals(1, dismissed.get());
        swipe(card, 200, 350);
        assertEquals(2, dismissed.get());
        swipe(card, 200, 180);
        assertEquals(2, dismissed.get());
        assertEquals(0f, card.getTranslationX(), 0f);
    }

    private static void swipe(SwipeDismissCardView card, float start, float end) {
        long t = android.os.SystemClock.uptimeMillis();
        dispatch(card, MotionEvent.ACTION_DOWN, start, t);
        dispatch(card, MotionEvent.ACTION_MOVE, end, t + 16);
        dispatch(card, MotionEvent.ACTION_UP, end, t + 32);
    }

    private static void dispatch(SwipeDismissCardView card, int action, float x, long time) {
        MotionEvent event = MotionEvent.obtain(time - 32, time, action, x, 30, 0);
        card.onTouchEvent(event);
        event.recycle();
    }
}
