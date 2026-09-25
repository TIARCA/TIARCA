package io.mrarm.irc.chat;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ChatSelectTouchListenerTest {

    @Test
    public void selectionLineIsClampedToItsOwnLayout() {
        Layout multiLine = layout("one\ntwo\nthree");
        Layout singleLine = layout("short");

        assertEquals(2, ChatSelectTouchListener.getSafeLineForOffset(
                multiLine, multiLine.getText().length()));
        assertEquals(0, ChatSelectTouchListener.getSafeLineForOffset(
                singleLine, multiLine.getText().length()));
    }

    @Test
    public void selectionOffsetIsClampedToCurrentLayoutText() {
        Layout layout = layout("short");

        assertEquals(0, ChatSelectTouchListener.clampLayoutOffset(layout, -4));
        assertEquals(5, ChatSelectTouchListener.clampLayoutOffset(layout, 200));
    }

    private static Layout layout(String text) {
        TextPaint paint = new TextPaint();
        paint.setTextSize(16f);
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, 1000)
                .build();
    }
}
