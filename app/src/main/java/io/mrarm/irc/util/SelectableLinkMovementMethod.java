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
    private LongClickableSpan pressedLongSpan;
    private boolean longPressTriggered;
    private final Runnable longPressRunnable = new Runnable() {
        @Override public void run() {
            if (pressedLongSpan != null && pressedWidget != null) {
                longPressTriggered = pressedLongSpan.onLongClick(pressedWidget);
            }
        }
    };
    private TextView pressedWidget;

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
            pressedLongSpan = getLongClickableSpan(widget, buffer, event);
            longPressTriggered = false;
            pressedWidget = pressedLongSpan == null ? null : widget;
            if (pressedLongSpan != null) {
                widget.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                return true;
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && pressedLongSpan != null &&
                !isWithinTouchSlop(widget, event)) {
            widget.removeCallbacks(longPressRunnable);
            pressedLongSpan = null;
            pressedWidget = null;
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP &&
                isShortTap(widget, event)) {
            widget.removeCallbacks(longPressRunnable);
            if (pressedLongSpan != null) {
                pressedLongSpan.onClick(widget);
                clearLongPress();
                return true;
            }
            ClickableSpan span = getClickableSpan(widget, buffer, event);
            if (span != null) {
                span.onClick(widget);
                return true;
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP && pressedLongSpan != null) {
            widget.removeCallbacks(longPressRunnable);
            boolean handled = longPressTriggered;
            clearLongPress();
            return handled;
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL && pressedLongSpan != null) {
            widget.removeCallbacks(longPressRunnable);
            clearLongPress();
            return true;
        }
        return super.onTouchEvent(widget, buffer, event);
    }

    private boolean isShortTap(TextView widget, MotionEvent event) {
        return event.getEventTime() - downTime <= ViewConfiguration.getTapTimeout() &&
                isWithinTouchSlop(widget, event);
    }

    private boolean isWithinTouchSlop(TextView widget, MotionEvent event) {
        int slop = ViewConfiguration.get(widget.getContext()).getScaledTouchSlop();
        float dx = event.getX() - downX;
        float dy = event.getY() - downY;
        return dx * dx + dy * dy <= slop * slop;
    }

    private void clearLongPress() {
        pressedLongSpan = null;
        pressedWidget = null;
        longPressTriggered = false;
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

    private static LongClickableSpan getLongClickableSpan(TextView widget, Spannable buffer,
                                                          MotionEvent event) {
        Layout layout = widget.getLayout();
        if (layout == null)
            return null;
        int x = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
        int y = (int) event.getY() - widget.getTotalPaddingTop() + widget.getScrollY();
        int line = layout.getLineForVertical(y);
        int offset = layout.getOffsetForHorizontal(line, x);
        LongClickableSpan[] spans = buffer.getSpans(offset, offset, LongClickableSpan.class);
        return spans.length == 0 ? null : spans[0];
    }
}
