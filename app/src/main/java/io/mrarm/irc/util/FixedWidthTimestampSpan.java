package io.mrarm.irc.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.style.ReplacementSpan;

import java.util.regex.Pattern;

public class FixedWidthTimestampSpan extends ReplacementSpan {

    private static final String MEASURE_NUMBER_CHARS = "1234567890";
    private static final Pattern sMatchNumbersRegex = Pattern.compile("[0-9]");

    private int mPreOffset;

    public FixedWidthTimestampSpan(int preOffset) {
        mPreOffset = preOffset;
    }

    /**
     * Returns the width required to render {@code timestamp} as if every digit had the width of
     * the widest digit in the current typeface. This is useful both for the legacy inline span and
     * for the dedicated left timestamp column used by chat rows.
     */
    public static int measureFixedTimestampWidth(@NonNull Paint paint,
                                                 @NonNull CharSequence timestamp) {
        return (int) Math.ceil(measureFixedTimestampWidthFloat(paint, timestamp));
    }

    private static float measureFixedTimestampWidthFloat(@NonNull Paint paint,
                                                         @NonNull CharSequence timestamp) {
        float[] numberWidths = new float[MEASURE_NUMBER_CHARS.length()];
        paint.getTextWidths(MEASURE_NUMBER_CHARS, numberWidths);
        float maxWidth = 0.f;
        char widestDigit = '0';
        for (int i = MEASURE_NUMBER_CHARS.length() - 1; i >= 0; --i) {
            if (numberWidths[i] > maxWidth) {
                maxWidth = numberWidths[i];
                widestDigit = MEASURE_NUMBER_CHARS.charAt(i);
            }
        }
        String widest = sMatchNumbersRegex.matcher(timestamp)
                .replaceAll(String.valueOf(widestDigit));
        return paint.measureText(widest);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                       @Nullable Paint.FontMetricsInt fm) {
        CharSequence timestamp = text.subSequence(start - mPreOffset, start);
        return (int) (measureFixedTimestampWidthFloat(paint, timestamp) -
                paint.measureText(timestamp, 0, timestamp.length()));
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
                     int top, int y, int bottom, @NonNull Paint paint) {
    }

}
