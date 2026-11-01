package io.mrarm.irc.util;

import android.content.Context;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Gestures must begin over the visible nickname, not the nearest character. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SelectableLinkMovementHitTest {

    private final ClickableSpan nickname = new ClickableSpan() {
        @Override public void onClick(View widget) { }
    };

    private TextView message(String text, int start, int end) {
        Context context = RuntimeEnvironment.getApplication();
        TextView widget = new TextView(context);
        widget.setPadding(16, 8, 16, 8);
        SpannableString contents = new SpannableString(text);
        contents.setSpan(nickname, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        widget.setText(contents, TextView.BufferType.SPANNABLE);
        widget.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(96, View.MeasureSpec.EXACTLY));
        widget.layout(0, 0, 600, 96);
        return widget;
    }

    private ClickableSpan hit(TextView view, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 1, MotionEvent.ACTION_DOWN, x, y, 0);
        try {
            return SelectableLinkMovementMethod.getClickableSpan(view,
                    (android.text.Spannable) view.getText(), event);
        } finally {
            event.recycle();
        }
    }

    private float lineY(TextView view) {
        Layout layout = view.getLayout();
        return view.getTotalPaddingTop()
                + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f;
    }

    private float textX(TextView view, int offset) {
        return view.getTotalPaddingLeft() + view.getLayout().getPrimaryHorizontal(offset);
    }

    @Test public void touchInsideNicknameFindsSpan() {
        TextView view = message("Alice dice ciao", 0, 5);
        assertSame(nickname, hit(view, (textX(view, 0) + textX(view, 5)) / 2f,
                lineY(view)));
    }

    @Test public void emptySpaceToRightOfNicknameDoesNotActivateSwipe() {
        TextView view = message("Alice", 0, 5);
        assertNull(hit(view, view.getWidth() - 24, lineY(view)));
    }

    @Test public void touchesOnSurroundingWordsDoNotActivateNicknameSwipe() {
        TextView view = message("da Alice: ciao", 3, 8);
        assertNull(hit(view, textX(view, 1), lineY(view)));
        assertNull(hit(view, textX(view, 10), lineY(view)));
    }

    @Test public void outsideTextVerticalBoundsDoesNotActivateSwipe() {
        TextView view = message("Alice", 0, 5);
        float x = (textX(view, 0) + textX(view, 5)) / 2f;
        assertNull(hit(view, x, -2f));
        assertNull(hit(view, x, view.getHeight() + 20f));
    }

    @Test public void longWrappedUrlCanBeTappedOnSecondVisualLine() {
        TextView view = new TextView(RuntimeEnvironment.getApplication());
        view.setPadding(8, 4, 8, 4);
        String url = "https://media.simosnap.com/files/6191f11adac6e0a5a9b0760838861f863928226602e7589b973f%20(1).gif";
        URLSpan link = new URLSpan(url);
        SpannableString contents = new SpannableString(url);
        contents.setSpan(link, 0, url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(contents, TextView.BufferType.SPANNABLE);
        view.measure(View.MeasureSpec.makeMeasureSpec(210, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, 210, view.getMeasuredHeight());
        Layout layout = view.getLayout();
        assertTrue("URL must wrap to multiple lines", layout.getLineCount() > 2);
        for (int line = 0; line < layout.getLineCount(); line++) {
            float middleX = view.getTotalPaddingLeft()
                    + (layout.getLineLeft(line) + layout.getLineRight(line)) / 2f;
            float middleY = view.getTotalPaddingTop()
                    + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f;
            assertSame("line " + line, link, hit(view, middleX, middleY));
        }
        assertNull(hit(view, view.getWidth() - 2f,
                view.getTotalPaddingTop() + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f));
    }

    @Test public void paddingAroundTextIsNotNickname() {
        TextView view = message("Alice", 0, 5);
        assertNull(hit(view, 2f, lineY(view)));
    }
}
