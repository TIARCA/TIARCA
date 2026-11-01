package io.mrarm.irc.guide;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

/** Regression test: expanded guide entries must not repeat their summary. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class GuideAdapterVisibilityTest {

    @Test
    public void collapsedEntryShowsOnlySummary() {
        TextView summary = new TextView(RuntimeEnvironment.getApplication());
        View detail = new FrameLayout(RuntimeEnvironment.getApplication());

        GuideAdapter.applyExpandedVisibility(summary, detail, false);

        assertEquals(View.VISIBLE, summary.getVisibility());
        assertEquals(View.GONE, detail.getVisibility());
    }

    @Test
    public void expandedEntryShowsOnlyDetails() {
        TextView summary = new TextView(RuntimeEnvironment.getApplication());
        View detail = new FrameLayout(RuntimeEnvironment.getApplication());

        GuideAdapter.applyExpandedVisibility(summary, detail, true);

        assertEquals(View.GONE, summary.getVisibility());
        assertEquals(View.VISIBLE, detail.getVisibility());
    }

    @Test
    public void recycledEntryResetsSummaryVisibility() {
        TextView summary = new TextView(RuntimeEnvironment.getApplication());
        View detail = new FrameLayout(RuntimeEnvironment.getApplication());

        GuideAdapter.applyExpandedVisibility(summary, detail, true);
        GuideAdapter.applyExpandedVisibility(summary, detail, false);

        assertEquals(View.VISIBLE, summary.getVisibility());
        assertEquals(View.GONE, detail.getVisibility());
    }
}
