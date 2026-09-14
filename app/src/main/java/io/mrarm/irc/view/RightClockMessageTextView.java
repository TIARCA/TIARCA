package io.mrarm.irc.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;

import io.mrarm.irc.R;
import io.mrarm.irc.config.RightClockSettings;
import io.mrarm.irc.util.AlignToPointSpan;
import io.mrarm.irc.util.DefaultPreferences;
import io.mrarm.irc.util.FixedWidthTimestampSpan;
import io.mrarm.irc.util.IRCColorUtils;

/**
 * Message TextView that moves the timestamp into a dedicated clock column.
 *
 * When the right-clock option is disabled the timestamp is placed at the far left, before any
 * avatar. When it is enabled the timestamp is placed at the far right. Keeping the timestamp out
 * of the message body makes avatar/no-avatar rows share the same left edge and avoids visual
 * jumps between user messages and events.
 */
public class RightClockMessageTextView extends AppCompatTextView
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private CharSequence mOriginalText;
    private BufferType mLastBufferType = BufferType.NORMAL;
    private boolean mInitialized;
    private boolean mApplying;

    public RightClockMessageTextView(Context context) {
        super(context);
        init();
    }

    public RightClockMessageTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RightClockMessageTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mInitialized = true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        DefaultPreferences.get(getContext()).registerOnSharedPreferenceChangeListener(this);
        if (mOriginalText != null)
            applyClockPlacement(mOriginalText, mLastBufferType);
    }

    @Override
    protected void onDetachedFromWindow() {
        DefaultPreferences.get(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (RightClockSettings.PREF_MESSAGE_TIME_RIGHT.equals(key) && mOriginalText != null)
            applyClockPlacement(mOriginalText, mLastBufferType);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (!mInitialized || mApplying) {
            super.setText(text, type);
            return;
        }
        mLastBufferType = type != null ? type : BufferType.NORMAL;
        mOriginalText = text;
        applyClockPlacement(text, mLastBufferType);
    }

    private void applyClockPlacement(CharSequence source, BufferType type) {
        mApplying = true;
        try {
            TextView leftClock = findClockView(R.id.chat_message_time_left);
            TextView rightClock = findClockView(R.id.chat_message_time);
            hideClock(leftClock);
            hideClock(rightClock);

            if (source == null) {
                super.setText(null, type);
                return;
            }

            TimestampRange range = findTimestampRange(source);
            if (range == null) {
                super.setText(source, type);
                return;
            }

            SpannableStringBuilder body = new SpannableStringBuilder(source);
            CharSequence timestamp = new SpannableString(body.subSequence(range.start, range.end));
            body.delete(range.start, range.end);

            // Remove one separator space left behind by common formats, but never alter message text.
            if (range.start < body.length() && body.charAt(range.start) == ' ')
                body.delete(range.start, range.start + 1);
            else if (range.start > 0 && body.charAt(range.start - 1) == ' ')
                body.delete(range.start - 1, range.start);

            boolean showAtRight = RightClockSettings.isEnabled(getContext());
            TextView clock = showAtRight ? rightClock : leftClock;
            if (clock == null) {
                super.setText(source, type);
                return;
            }

            CharSequence cleanTimestamp = trimTimestamp(timestamp);
            copyTextStyle(clock);
            clock.setText(cleanTimestamp);
            if (showAtRight) {
                clock.setMinWidth(0);
            } else {
                int fixedContentWidth = FixedWidthTimestampSpan.measureFixedTimestampWidth(
                        clock.getPaint(), cleanTimestamp);
                clock.setMinWidth(fixedContentWidth + clock.getPaddingLeft() +
                        clock.getPaddingRight());
            }
            clock.setVisibility(View.VISIBLE);

            // Moving the timestamp changes the horizontal position of the wrap anchor. The
            // presentation span is NoCopySpan, so AlignToPointSpan.apply() also recreates it from
            // the surviving anchor before recalculating the continuation-line indentation.
            AlignToPointSpan.apply(this, body);
            super.setText(body, type);
        } finally {
            mApplying = false;
        }
    }

    private void copyTextStyle(TextView clock) {
        clock.setTypeface(getTypeface());
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getTextSize());
    }

    private void hideClock(TextView clock) {
        if (clock != null)
            clock.setVisibility(View.GONE);
    }

    private TextView findClockView(int id) {
        if (getParent() instanceof View)
            return ((View) getParent()).findViewById(id);
        return null;
    }

    private TimestampRange findTimestampRange(CharSequence source) {
        if (!(source instanceof Spanned))
            return null;
        Spanned spanned = (Spanned) source;
        ForegroundColorSpan[] spans = spanned.getSpans(0, source.length(), ForegroundColorSpan.class);
        int timestampColor = IRCColorUtils.getTimestampTextColor(getContext());
        TimestampRange best = null;
        for (ForegroundColorSpan span : spans) {
            if (span.getForegroundColor() != timestampColor)
                continue;
            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);
            if (start < 0 || end <= start)
                continue;
            if (best == null || end - start > best.end - best.start)
                best = new TimestampRange(start, end);
        }
        return best;
    }

    private CharSequence trimTimestamp(CharSequence timestamp) {
        int start = 0;
        int end = timestamp.length();
        while (start < end && Character.isWhitespace(timestamp.charAt(start)))
            start++;
        while (end > start && Character.isWhitespace(timestamp.charAt(end - 1)))
            end--;
        if (start == 0 && end == timestamp.length())
            return timestamp;
        if (timestamp instanceof Spannable)
            return new SpannableString(((Spannable) timestamp).subSequence(start, end));
        return timestamp.subSequence(start, end);
    }

    private static class TimestampRange {
        final int start;
        final int end;

        TimestampRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
