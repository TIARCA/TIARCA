package io.mrarm.irc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Regression coverage for edge-to-edge ActionBar/content overlap calculations. */
public class ActionBarInsetTest {

    @Test
    public void contentDrawnBehindBothStatusBarAndActionBarUsesFullOffset() {
        assertEquals(154, ThemedActivity.actionBarOverlap(0, 54, 100));
    }

    @Test
    public void contentAlreadyBelowStatusBarOnlyNeedsActionBarOffset() {
        assertEquals(100, ThemedActivity.actionBarOverlap(54, 54, 100));
    }

    @Test
    public void contentAlreadyBelowActionBarNeedsNoExtraPadding() {
        assertEquals(0, ThemedActivity.actionBarOverlap(154, 54, 100));
    }

    @Test
    public void partiallyOverlappingContentOnlyAddsMissingSpace() {
        assertEquals(40, ThemedActivity.actionBarOverlap(114, 54, 100));
    }
}
