package io.mrarm.irc.util;

import android.text.Layout;
import android.text.Spannable;
import android.text.method.ArrowKeyMovementMethod;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

/** Keeps native text selection while allowing short taps on clickable spans. */
public final class SelectableLinkMovementMethod extends ArrowKeyMovementMethod {

    private static final SelectableLinkMovementMethod INSTANCE =
            new SelectableLinkMovementMethod();

    private float downX;
    private float downY;
    private long downTime;

    private SelectableLinkMovementMethod() {
    }

    public static SelectableLinkMovementMethod getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            downTime = event.getEventTime();
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP &&
                isShortTap(widget, event)) {
            ClickableSpan span = getClickableSpan(widget, buffer, event);
            if (span != null) {
                span.onClick(widget);
                return true;
            }
        }
        return super.onTouchEvent(widget, buffer, event);
    }

    private boolean isShortTap(TextView widget, MotionEvent event) {
        int slop = ViewConfiguration.get(widget.getContext()).getScaledTouchSlop();
        float dx = event.getX() - downX;
        float dy = event.getY() - downY;
        return event.getEventTime() - downTime <= ViewConfiguration.getTapTimeout() &&
                dx * dx + dy * dy <= slop * slop;
    }

    private static ClickableSpan getClickableSpan(TextView widget, Spannable buffer,
                                                   MotionEvent event) {
        Layout layout = widget.getLayout();
        if (layout == null)
            return null;
        int x = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
        int y = (int) event.getY() - widget.getTotalPaddingTop() + widget.getScrollY();
        int line = layout.getLineForVertical(y);
        int offset = layout.getOffsetForHorizontal(line, x);
        ClickableSpan[] spans = buffer.getSpans(offset, offset, ClickableSpan.class);
        return spans.length == 0 ? null : spans[0];
    }
}
