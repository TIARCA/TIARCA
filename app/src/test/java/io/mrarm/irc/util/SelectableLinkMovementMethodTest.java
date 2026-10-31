package io.mrarm.irc.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelectableLinkMovementMethodTest {

    @Test
    public void spanClickDoesNotDependOnTheShortTapTimeout() {
        assertTrue(SelectableLinkMovementMethod.shouldInvokeSpanClick(false, true));
    }

    @Test
    public void swipeMustStartHorizontallyBeyondTouchSlop() {
        assertTrue(SelectableLinkMovementMethod.isHorizontalSwipeIntent(12, 2, 8));
        assertFalse(SelectableLinkMovementMethod.isHorizontalSwipeIntent(7, 0, 8));
        assertFalse(SelectableLinkMovementMethod.isHorizontalSwipeIntent(12, 18, 8));
    }

    @Test
    public void swipeRequiresCommittedTravelAndDirection() {
        assertTrue(SelectableLinkMovementMethod.getCompletedSwipeDirection(50, 0, 40) == 1);
        assertTrue(SelectableLinkMovementMethod.getCompletedSwipeDirection(-50, 0, 40) == -1);
        assertTrue(SelectableLinkMovementMethod.getCompletedSwipeDirection(25, 0, 40) == 0);
        assertTrue(SelectableLinkMovementMethod.getCompletedSwipeDirection(50, 45, 40) == 0);
    }

    @Test
    public void longPressAndDraggingDoNotInvokeTheTapAction() {
        assertFalse(SelectableLinkMovementMethod.shouldInvokeSpanClick(true, true));
        assertFalse(SelectableLinkMovementMethod.shouldInvokeSpanClick(false, false));
    }
}
