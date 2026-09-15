package io.mrarm.irc.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MessageFormatSettingsFormatBarLayoutTest {

    @Test
    public void addElementsHintCannotExpandWrapContentToolbar() {
        Context base = ApplicationProvider.getApplicationContext();
        Context themed = new ContextThemeWrapper(base, R.style.AppTheme);
        MessageFormatSettingsFormatBar bar = new MessageFormatSettingsFormatBar(themed);
        RelativeLayout inner = bar.findViewById(R.id.formatting_bar);
        assertNotNull(inner);

        TextView hint = findHint(inner, themed.getString(R.string.message_format_add_elements_hint));
        assertNotNull(hint);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, hint.getLayoutParams().height);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) hint.getLayoutParams();
        assertEquals(RelativeLayout.TRUE, params.getRule(RelativeLayout.CENTER_VERTICAL));

        int width = dp(themed, 360);
        int maxHeight = dp(themed, 500);
        bar.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
        assertTrue("format bar unexpectedly filled the viewport",
                bar.getMeasuredHeight() < dp(themed, 120));
    }

    private static TextView findHint(ViewGroup parent, String expected) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView && expected.contentEquals(((TextView) child).getText()))
                return (TextView) child;
            if (child instanceof ViewGroup) {
                TextView nested = findHint((ViewGroup) child, expected);
                if (nested != null)
                    return nested;
            }
        }
        return null;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
