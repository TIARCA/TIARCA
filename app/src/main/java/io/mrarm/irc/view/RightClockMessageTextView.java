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
import io.mrarm.irc.util.DefaultPreferences;
import io.mrarm.irc.util.IRCColorUtils;

/**
 * Message TextView that can move the timestamp to a dedicated TextView at the far right.
 *
 * The existing MessageBuilder remains the source of truth for formatting. When the option is
 * enabled, this view identifies the rendered timestamp by its timestamp colour span, removes it
 * from the message body and places the original styled timestamp into chat_message_time.
 */
public class RightClockMessageTextView extends AppCompatTextView
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private CharSequence mOriginalText;
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
            applyRightClock(mOriginalText, getBufferType());
    }

    @Override
    protected void onDetachedFromWindow() {
        DefaultPreferences.get(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (RightClockSettings.PREF_MESSAGE_TIME_RIGHT.equals(key) && mOriginalText != null)
            applyRightClock(mOriginalText, getBufferType());
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (!mInitialized || mApplying) {
            super.setText(text, type);
            return;
        }
        mOriginalText = text;
        applyRightClock(text, type);
    }

    private void applyRightClock(CharSequence source, BufferType type) {
        mApplying = true;
        try {
            TextView clock = findClockView();
            if (!RightClockSettings.isEnabled(getContext()) || clock == null || source == null) {
                if (clock != null)
                    clock.setVisibility(View.GONE);
                super.setText(source, type);
                return;
            }

            TimestampRange range = findTimestampRange(source);
            if (range == null) {
                clock.setVisibility(View.GONE);
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

            clock.setTypeface(getTypeface());
            clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getTextSize());
            clock.setText(trimTimestamp(timestamp));
            clock.setVisibility(View.VISIBLE);
            super.setText(new SpannableString(body), type);
        } finally {
            mApplying = false;
        }
    }

    private TextView findClockView() {
        if (getParent() instanceof View)
            return ((View) getParent()).findViewById(R.id.chat_message_time);
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
