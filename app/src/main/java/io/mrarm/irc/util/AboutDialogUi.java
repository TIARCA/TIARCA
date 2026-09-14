package io.mrarm.irc.util;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Layout-only helpers for the About dialog. */
public final class AboutDialogUi {

    private AboutDialogUi() {
    }

    public static void compactBody(TextView body) {
        if (body == null || body.getText() == null)
            return;
        String text = body.getText().toString();
        // Keep paragraph boundaries, but avoid full blank rows that consume scarce dialog height.
        body.setText(text.replaceAll("\\n[ \\t]*\\n+", "\\n"));
    }

    public static void compactDebugActions(Context context, LinearLayout debugPanel,
                                           Button share, Button clear, Button disable) {
        if (debugPanel == null)
            return;
        debugPanel.removeView(share);
        debugPanel.removeView(clear);
        debugPanel.removeView(disable);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_HORIZONTAL);

        int gap = dp(context, 4);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(gap, 0, gap, 0);
        row.addView(share, new LinearLayout.LayoutParams(buttonParams));
        row.addView(clear, new LinearLayout.LayoutParams(buttonParams));
        debugPanel.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams disableParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        disableParams.gravity = Gravity.CENTER_HORIZONTAL;
        disableParams.topMargin = dp(context, 2);
        debugPanel.addView(disable, disableParams);
    }

    public static ScrollView wrapScrollable(Context context, LinearLayout content) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
