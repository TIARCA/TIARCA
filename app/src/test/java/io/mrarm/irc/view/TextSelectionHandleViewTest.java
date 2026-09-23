package io.mrarm.irc.view;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TextSelectionHandleViewTest {

    @Test
    public void dragReportsLogicalCursorAboveHandleTop() {
        Context context = ApplicationProvider.getApplicationContext();
        GradientDrawable drawable = new GradientDrawable();
        drawable.setSize(40, 40);
        TextSelectionHandleView view = new TextSelectionHandleView(context, drawable, 30);

        float[] moved = new float[] { Float.NaN, Float.NaN };
        view.setOnMoveListener(new TextSelectionHandleView.MoveListener() {
            @Override
            public void onMoveStarted() {
            }

            @Override
            public void onMoveFinished() {
            }

            @Override
            public void onMoved(float x, float y) {
                moved[0] = x;
                moved[1] = y;
            }
        });

        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 20f, 0);
        MotionEvent move = MotionEvent.obtain(0, 10, MotionEvent.ACTION_MOVE, 30f, 50f, 0);
        try {
            view.onTouchEvent(down);
            view.onTouchEvent(move);
        } finally {
            down.recycle();
            move.recycle();
        }

        // X still tracks the drawable hotspot. Y is shifted 30% of the handle height upward so
        // TextView receives the logical cursor position rather than the handle's top edge.
        assertEquals(50f, moved[0], 0.01f);
        assertEquals(18f, moved[1], 0.01f);
    }
}
