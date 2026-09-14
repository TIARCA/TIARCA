package io.mrarm.irc.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.NoCopySpan;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;
import android.widget.TextView;

public class AlignToPointSpan implements LeadingMarginSpan, NoCopySpan {

    private Anchor mAnchor;
    private int mMargin = 0;

    public AlignToPointSpan(Anchor anchor) {
        mAnchor = anchor;
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return first ? 0 : mMargin;
    }

    @Override
    public void drawLeadingMargin(Canvas canvas, Paint paint, int i, int i1, int i2, int i3, int i4,
                                  CharSequence charSequence, int i5, int i6, boolean b,
                                  Layout layout) {
        // stub
    }

    public static class Anchor {
    }

    public static CharSequence apply(TextView textView, CharSequence text) {
        if (text == null || !(text instanceof Spannable))
            return text;
        Spannable s = (Spannable) text;
        AlignToPointSpan[] spans = s.getSpans(0, text.length(), AlignToPointSpan.class);

        // AlignToPointSpan is intentionally NoCopySpan, but the zero-width Anchor is copyable.
        // Message views may transform the rendered text (for example by moving the timestamp to a
        // dedicated column), so recreate the presentation span from any surviving anchors.
        if (spans.length == 0) {
            Anchor[] anchors = s.getSpans(0, text.length(), Anchor.class);
            for (Anchor anchor : anchors)
                s.setSpan(new AlignToPointSpan(anchor), 0, text.length(),
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            spans = s.getSpans(0, text.length(), AlignToPointSpan.class);
        }

        for (AlignToPointSpan span : spans) {
            int anchorPosition = s.getSpanStart(span.mAnchor);
            if (anchorPosition < 0)
                continue;
            span.mMargin = (int) Layout.getDesiredWidth(text, 0, anchorPosition,
                    textView.getPaint());
        }
        return text;
    }

}
