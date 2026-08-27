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
    public void longPressAndDraggingDoNotInvokeTheTapAction() {
        assertFalse(SelectableLinkMovementMethod.shouldInvokeSpanClick(true, true));
        assertFalse(SelectableLinkMovementMethod.shouldInvokeSpanClick(false, false));
    }
}
