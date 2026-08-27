package io.mrarm.irc.util;

import android.text.Layout;
import android.text.Selection;
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
    private ClickableSpan pressedSpan;
    private LongClickableSpan pressedLongSpan;
    private Spannable pressedBuffer;
    private boolean longPressTriggered;
    private boolean longPressInvoked;
    private final Runnable longPressRunnable = new Runnable() {
        @Override public void run() {
            if (pressedLongSpan != null && pressedWidget != null) {
                // The TextView's native long-press starts text selection. It is not wanted
                // when the gesture was targeted at a nickname span.
                pressedWidget.cancelLongPress();
                if (pressedBuffer != null)
                    Selection.removeSelection(pressedBuffer);
                longPressInvoked = true;
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
            pressedSpan = getClickableSpan(widget, buffer, event);
            pressedLongSpan = pressedSpan instanceof LongClickableSpan
                    ? (LongClickableSpan) pressedSpan : null;
            longPressTriggered = false;
            longPressInvoked = false;
            pressedWidget = pressedLongSpan == null ? null : widget;
            if (pressedLongSpan != null) {
                // Consume only nickname-span long presses. Normal message text keeps the
                // TextView's native selection/copy behavior.
                pressedBuffer = buffer;
                widget.cancelLongPress();
                widget.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                return true;
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && pressedSpan != null &&
                !isWithinTouchSlop(widget, event)) {
            widget.removeCallbacks(longPressRunnable);
            clearPressedSpan();
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP && pressedLongSpan != null) {
            widget.removeCallbacks(longPressRunnable);
            boolean handled = longPressTriggered;
            if (shouldInvokeSpanClick(longPressInvoked, isWithinTouchSlop(widget, event))) {
                pressedLongSpan.onClick(widget);
                handled = true;
            }
            clearPressedSpan();
            return handled;
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP && pressedSpan != null) {
            ClickableSpan span = pressedSpan;
            boolean handled = isWithinTouchSlop(widget, event);
            clearPressedSpan();
            if (handled) {
                span.onClick(widget);
                return true;
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL && pressedSpan != null) {
            widget.removeCallbacks(longPressRunnable);
            clearPressedSpan();
        }
        return super.onTouchEvent(widget, buffer, event);
    }

    static boolean shouldInvokeSpanClick(boolean longPressInvoked, boolean withinTouchSlop) {
        return !longPressInvoked && withinTouchSlop;
    }

    private boolean isWithinTouchSlop(TextView widget, MotionEvent event) {
        int slop = ViewConfiguration.get(widget.getContext()).getScaledTouchSlop();
        float dx = event.getX() - downX;
        float dy = event.getY() - downY;
        return dx * dx + dy * dy <= slop * slop;
    }

    private void clearPressedSpan() {
        pressedSpan = null;
        pressedLongSpan = null;
        pressedBuffer = null;
        pressedWidget = null;
        longPressTriggered = false;
        longPressInvoked = false;
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
