package io.mrarm.irc.util;

import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.ArrowKeyMovementMethod;
import android.text.style.ClickableSpan;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

/** Keeps native text selection and nickname taps/long presses while supporting nickname swipes. */
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
    private boolean horizontalSwipeActive;
    private NicknameSwipeFeedbackDrawable swipeFeedback;
    private TextView feedbackWidget;
    private int feedbackDirection;
    private final Runnable longPressRunnable = new Runnable() {
        @Override public void run() {
            if (pressedLongSpan != null && pressedWidget != null && !horizontalSwipeActive) {
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
        int masked = event.getActionMasked();
        if (masked == MotionEvent.ACTION_DOWN) {
            if (pressedWidget != null)
                pressedWidget.removeCallbacks(longPressRunnable);
            clearPressedSpan();
            downX = event.getX();
            downY = event.getY();
            pressedSpan = getClickableSpan(widget, buffer, event);
            pressedLongSpan = pressedSpan instanceof LongClickableSpan
                    ? (LongClickableSpan) pressedSpan : null;
            pressedWidget = pressedLongSpan == null ? null : widget;
            if (pressedLongSpan != null) {
                // Only nickname spans use custom gestures. Other text keeps native selection.
                pressedBuffer = buffer;
                widget.cancelLongPress();
                widget.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                return true;
            }
        } else if (masked == MotionEvent.ACTION_POINTER_DOWN && pressedSpan != null) {
            widget.removeCallbacks(longPressRunnable);
            clearPressedSpan();
            return false;
        } else if (masked == MotionEvent.ACTION_MOVE && pressedSpan != null) {
            if (longPressInvoked)
                return true;
            if (horizontalSwipeActive) {
                updateSwipeFeedback(widget, event);
                return true;
            }
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            int slop = ViewConfiguration.get(widget.getContext()).getScaledTouchSlop();
            if (pressedSpan instanceof SwipeableLongClickableSpan
                    && isHorizontalSwipeIntent(dx, dy, slop)) {
                horizontalSwipeActive = true;
                widget.removeCallbacks(longPressRunnable);
                if (widget.getParent() != null)
                    widget.getParent().requestDisallowInterceptTouchEvent(true);
                updateSwipeFeedback(widget, event);
                return true;
            }
            if (!isWithinTouchSlop(widget, event) && Math.abs(dy) >= Math.abs(dx)) {
                // Release predominantly vertical drags to the RecyclerView. A diagonal
                // gesture can still become horizontal without losing its nickname span.
                widget.removeCallbacks(longPressRunnable);
                clearPressedSpan();
            }
        } else if (masked == MotionEvent.ACTION_UP && pressedLongSpan != null) {
            widget.removeCallbacks(longPressRunnable);
            if (horizontalSwipeActive) {
                float minDistance = 40f * widget.getResources().getDisplayMetrics().density;
                int direction = getCompletedSwipeDirection(event.getX() - downX,
                        event.getY() - downY, minDistance);
                boolean handled = false;
                if (direction != 0 && pressedLongSpan instanceof SwipeableLongClickableSpan) {
                    handled = ((SwipeableLongClickableSpan) pressedLongSpan)
                            .onSwipe(widget, direction > 0);
                    if (handled && feedbackDirection != direction)
                        widget.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                clearPressedSpan();
                // Even an incomplete swipe must not accidentally trigger the nickname tap.
                return true;
            }
            boolean handled = longPressTriggered;
            if (shouldInvokeSpanClick(longPressInvoked, isWithinTouchSlop(widget, event))) {
                pressedLongSpan.onClick(widget);
                handled = true;
            }
            clearPressedSpan();
            return handled;
        } else if (masked == MotionEvent.ACTION_UP && pressedSpan != null) {
            ClickableSpan span = pressedSpan;
            boolean handled = isWithinTouchSlop(widget, event);
            clearPressedSpan();
            if (handled) {
                span.onClick(widget);
                return true;
            }
        } else if (masked == MotionEvent.ACTION_CANCEL && pressedSpan != null) {
            widget.removeCallbacks(longPressRunnable);
            clearPressedSpan();
        }
        return super.onTouchEvent(widget, buffer, event);
    }

    static boolean shouldInvokeSpanClick(boolean longPressInvoked, boolean withinTouchSlop) {
        return !longPressInvoked && withinTouchSlop;
    }

    static boolean isHorizontalSwipeIntent(float dx, float dy, float touchSlop) {
        return Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.5f;
    }

    static int getCompletedSwipeDirection(float dx, float dy, float minDistance) {
        if (Math.abs(dx) < minDistance || Math.abs(dx) <= Math.abs(dy) * 1.5f)
            return 0;
        return dx > 0 ? 1 : -1;
    }

    static float getSwipeProgress(float dx, float minDistance) {
        return minDistance <= 0f ? 1f : Math.min(1f, Math.abs(dx) / minDistance);
    }

    private void updateSwipeFeedback(TextView widget, MotionEvent event) {
        if (!(pressedSpan instanceof SwipeableLongClickableSpan) || pressedBuffer == null)
            return;
        float dx = event.getX() - downX;
        float dy = event.getY() - downY;
        float distance = 40f * widget.getResources().getDisplayMetrics().density;
        int direction = getCompletedSwipeDirection(dx, dy, distance);
        boolean right = dx >= 0;
        String action = ((SwipeableLongClickableSpan) pressedSpan).getSwipeAction(right);

        if (swipeFeedback == null || feedbackWidget != widget) {
            removeSwipeFeedback();
            swipeFeedback = new NicknameSwipeFeedbackDrawable(widget);
            feedbackWidget = widget;
            widget.getOverlay().add(swipeFeedback);
        }
        swipeFeedback.update(pressedBuffer, pressedSpan, action, right, dx,
                getSwipeProgress(dx, distance));
        if (direction != 0 && feedbackDirection != direction && !"none".equals(action)) {
            widget.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            feedbackDirection = direction;
        }
    }

    private void removeSwipeFeedback() {
        if (feedbackWidget != null && swipeFeedback != null) {
            feedbackWidget.getOverlay().remove(swipeFeedback);
            feedbackWidget.invalidate();
        }
        feedbackWidget = null;
        swipeFeedback = null;
        feedbackDirection = 0;
    }

    private boolean isWithinTouchSlop(TextView widget, MotionEvent event) {
        int slop = ViewConfiguration.get(widget.getContext()).getScaledTouchSlop();
        float dx = event.getX() - downX;
        float dy = event.getY() - downY;
        return dx * dx + dy * dy <= slop * slop;
    }

    private void clearPressedSpan() {
        removeSwipeFeedback();
        pressedSpan = null;
        pressedLongSpan = null;
        pressedBuffer = null;
        pressedWidget = null;
        longPressTriggered = false;
        longPressInvoked = false;
        horizontalSwipeActive = false;
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
