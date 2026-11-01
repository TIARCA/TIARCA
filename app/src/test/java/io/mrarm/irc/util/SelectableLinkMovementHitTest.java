package io.mrarm.irc.util;

import android.content.Context;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
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

    @Test public void spaceBeforeOrAfterNicknameDoesNotActivateSwipe() {
        TextView view = message("da Alice: ciao", 3, 8);
        assertNull(hit(view, textX(view, 1), lineY(view)));
        assertNull(hit(view, textX(view, 10), lineY(view)));
        // Use a point inside a specific glyph, not an average over a possibly
        // differently shaped text run in Robolectric's simplified font layout.
        float insideX = (textX(view, 4) + textX(view, 5)) / 2f;
        Layout layout = view.getLayout();
        String coords = "x1=" + textX(view, 1) + " x3=" + textX(view, 3)
                + " x4=" + textX(view, 4) + " x5=" + textX(view, 5)
                + " x8=" + textX(view, 8) + " x10=" + textX(view, 10)
                + " nearest=" + layout.getOffsetForHorizontal(0,
                    insideX - view.getTotalPaddingLeft()) + " insideX=" + insideX;
        assertSame(coords, nickname, hit(view, insideX, lineY(view)));
    }

    @Test public void outsideTextVerticalBoundsDoesNotActivateSwipe() {
        TextView view = message("Alice", 0, 5);
        float x = (textX(view, 0) + textX(view, 5)) / 2f;
        assertNull(hit(view, x, -2f));
        assertNull(hit(view, x, view.getHeight() + 20f));
    }

    @Test public void paddingAroundTextIsNotNickname() {
        TextView view = message("Alice", 0, 5);
        assertNull(hit(view, 2f, lineY(view)));
    }
}
