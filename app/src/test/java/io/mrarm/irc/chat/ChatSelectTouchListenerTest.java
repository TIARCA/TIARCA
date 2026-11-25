package io.mrarm.irc.chat;

import android.app.Activity;
import android.text.Layout;
import androidx.recyclerview.widget.RecyclerView;
import org.robolectric.Robolectric;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
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

    @Test
    public void disposingSelectionUnregistersObserverAndCannotReattach() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        RecyclerView recycler = new RecyclerView(activity);
        activity.setContentView(recycler);
        ChatSelectTouchListener listener = new ChatSelectTouchListener(recycler);
        recycler.addOnItemTouchListener(listener);
        // Explicitly exercise the attach callback even on a headless window.
        listener.onViewAttachedToWindow(recycler);
        java.lang.reflect.Field observer = ChatSelectTouchListener.class.getDeclaredField("mRegisteredObserver");
        observer.setAccessible(true);
        assertNotNull(observer.get(listener));
        listener.onViewDetachedFromWindow(recycler);
        assertNull(observer.get(listener));
        listener.onViewAttachedToWindow(recycler);
        assertNotNull(observer.get(listener));
        listener.dispose();
        listener.dispose();
        listener.onViewAttachedToWindow(recycler);
        assertNull(observer.get(listener));
    }

    private static Layout layout(String text) {
        TextPaint paint = new TextPaint();
        paint.setTextSize(16f);
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, 1000)
                .build();
    }
}
