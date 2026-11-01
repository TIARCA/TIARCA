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

    private TextView urlMessage(String address, android.text.style.URLSpan url, int width) {
        TextView view = new TextView(RuntimeEnvironment.getApplication());
        view.setPadding(16, 8, 16, 8);
        SpannableString content = new SpannableString(address);
        content.setSpan(url, 0, address.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(content, TextView.BufferType.SPANNABLE);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, width, view.getMeasuredHeight());
        return view;
    }

    @Test public void wrappedUrlIsClickableOnContinuationLine() {
        android.text.style.URLSpan url = new android.text.style.URLSpan("https://example.org/long.gif");
        // Robolectric does not reliably soft-wrap unbroken URLs, so force a
        // visual continuation line while retaining one URLSpan over both lines.
        TextView view = urlMessage(
                "https://media.simosnap.com/files/6191f11adac6e0a5a9b0760838861f86/\n3928226602e7589b973f%20(1).gif",
                url, 180);
        Layout layout = view.getLayout();
        org.junit.Assert.assertTrue("URL should wrap", layout.getLineCount() >= 2);
        int line = 1;
        int lineStart = layout.getLineStart(line);
        float x = view.getTotalPaddingLeft()
                + (layout.getPrimaryHorizontal(lineStart)
                + layout.getPrimaryHorizontal(lineStart + 1)) / 2f;
        float y = view.getTotalPaddingTop()
                + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f;
        assertSame(url, hit(view, x, y));
    }

    @Test public void emptyAreaBesideWrappedUrlDoesNotActivateIt() {
        android.text.style.URLSpan url = new android.text.style.URLSpan("https://example.org");
        TextView view = urlMessage(
                "https://example.org/a-long-resource/path/to/image.gif", url, 190);
        assertNull(hit(view, view.getWidth() - 2, view.getTotalPaddingTop() + 2));
    }

    @Test public void paddingAroundTextIsNotNickname() {
        TextView view = message("Alice", 0, 5);
        assertNull(hit(view, 2f, lineY(view)));
    }
}
