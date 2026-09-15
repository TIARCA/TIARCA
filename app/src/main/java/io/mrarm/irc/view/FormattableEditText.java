package io.mrarm.irc.view;

import android.content.Context;
import android.graphics.Rect;
import androidx.appcompat.widget.AppCompatEditText;
import android.text.Editable;
import android.text.Layout;
import android.text.NoCopySpan;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import io.mrarm.irc.R;
import io.mrarm.irc.util.MessageBuilder;

public class FormattableEditText extends AppCompatEditText {

    private TextFormatBar mFormatBar;
    private boolean mSettingText = false;

    public FormattableEditText(Context context) {
        super(context);
        init();
    }

    public FormattableEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FormattableEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        addTextChangedListener(new TextWatcher() {
            private List<SpanData> mBackedUpSpans = new ArrayList<>();

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (mSettingText)
                    return;
                int selStart = getSelectionStart();
                for (Object span : getText().getSpans(start, start + count, Object.class)) {
                    int flags = getText().getSpanFlags(span);
                    if (span instanceof NoCopySpan || (flags & Spanned.SPAN_COMPOSING) != 0
                            || (flags & Spanned.SPAN_PARAGRAPH) != 0)
                        continue;
                    SpanData data = new SpanData();
                    data.span = span;
                    data.start = getText().getSpanStart(span);
                    data.end = getText().getSpanEnd(span);
                    data.flags = flags;
                    int spanPointFlags = flags & Spanned.SPAN_POINT_MARK_MASK;
                    if ((data.start >= selStart || data.start >= start + count) &&
                            !(data.start == selStart && (spanPointFlags == Spanned.SPAN_INCLUSIVE_EXCLUSIVE || spanPointFlags == Spanned.SPAN_INCLUSIVE_INCLUSIVE)) &&
                            !(data.end == selStart && (spanPointFlags == Spanned.SPAN_EXCLUSIVE_INCLUSIVE || spanPointFlags == Spanned.SPAN_INCLUSIVE_INCLUSIVE)))
                        data.start += after - count;
                    if (data.end == selStart &&
                            (spanPointFlags == Spanned.SPAN_EXCLUSIVE_INCLUSIVE ||
                                    spanPointFlags == Spanned.SPAN_INCLUSIVE_INCLUSIVE)) {
                        data.extendToCursor = true;
                    } else if (data.end > selStart) {
                        data.end = Math.max(data.end + after - count, data.start);
                    }
                    mBackedUpSpans.add(data);
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mSettingText)
                    return;
                int selStart = getSelectionStart();
                for (SpanData span : mBackedUpSpans) {
                    if (span.extendToCursor)
                        span.end = selStart;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (mSettingText)
                    return;
                mSettingText = true;
                for (SpanData span : mBackedUpSpans) {
                    span.start = Math.max(span.start, 0);
                    span.end = Math.min(span.end, s.length());
                    int spanPointFlags = span.flags & Spanned.SPAN_POINT_MARK_MASK;
                    if (span.start >= s.length() || span.end < 0 || span.start > span.end ||
                            (span.start == span.end && (spanPointFlags == Spanned.SPAN_EXCLUSIVE_EXCLUSIVE || spanPointFlags == Spanned.SPAN_INCLUSIVE_EXCLUSIVE))) {
                        s.removeSpan(span.span);
                        continue;
                    }
                    s.setSpan(span.span, span.start, span.end, span.flags);
                }
                mBackedUpSpans.clear();
                mSettingText = false;
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        int presetButtonId = getLegacyPresetButtonId(getId());
        if (presetButtonId == 0)
            return;
        View presetButton = getRootView().findViewById(presetButtonId);
        if (presetButton != null)
            presetButton.setVisibility(View.GONE);
        // These editors no longer reserve trailing space for the removed whole-format preset arrow.
        setPaddingRelative(getPaddingStart(), getPaddingTop(), 0, getPaddingBottom());
    }

    static int getLegacyPresetButtonId(int editTextId) {
        if (editTextId == R.id.message_format_normal)
            return R.id.message_format_normal_preset;
        if (editTextId == R.id.message_format_normal_mention)
            return R.id.message_format_normal_mention_preset;
        if (editTextId == R.id.message_format_action)
            return R.id.message_format_action_preset;
        if (editTextId == R.id.message_format_action_mention)
            return R.id.message_format_action_mention_preset;
        if (editTextId == R.id.message_format_notice)
            return R.id.message_format_notice_preset;
        if (editTextId == R.id.message_format_event)
            return R.id.message_format_event_preset;
        return 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = super.onTouchEvent(event);
        if (event.getActionMasked() != MotionEvent.ACTION_UP)
            return handled;

        Layout layout = getLayout();
        Editable text = getText();
        if (layout == null || text == null || text.length() == 0)
            return handled;

        float x = event.getX() - getTotalPaddingLeft() + getScrollX();
        int y = Math.round(event.getY() - getTotalPaddingTop() + getScrollY());
        if (x < 0 || y < 0)
            return handled;

        int line = layout.getLineForVertical(y);
        MessageBuilder.MetaChipSpan chip = findMetaChipAtPosition(layout, text, line, x);
        if (chip == null)
            return handled;

        int start = text.getSpanStart(chip);
        int end = text.getSpanEnd(chip);
        if (start < 0 || end <= start)
            return handled;

        requestFocus();
        setSelection(start, end);
        return true;
    }

    static MessageBuilder.MetaChipSpan findMetaChipAtOffset(Spanned text, int offset) {
        if (text == null || text.length() == 0)
            return null;
        int safeOffset = Math.max(0, Math.min(offset, text.length() - 1));
        for (MessageBuilder.MetaChipSpan chip : text.getSpans(0, text.length(),
                MessageBuilder.MetaChipSpan.class)) {
            int start = text.getSpanStart(chip);
            int end = text.getSpanEnd(chip);
            if (start >= 0 && end > start && safeOffset >= start && safeOffset < end)
                return chip;
        }
        return null;
    }

    private static MessageBuilder.MetaChipSpan findMetaChipAtPosition(Layout layout, Spanned text,
                                                                       int line, float x) {
        for (MessageBuilder.MetaChipSpan chip : text.getSpans(0, text.length(),
                MessageBuilder.MetaChipSpan.class)) {
            int start = text.getSpanStart(chip);
            int end = text.getSpanEnd(chip);
            if (start < 0 || end <= start || layout.getLineForOffset(start) != line)
                continue;
            float left = layout.getPrimaryHorizontal(start);
            float right = layout.getPrimaryHorizontal(end);
            if (x >= Math.min(left, right) && x <= Math.max(left, right))
                return chip;
        }
        int offset = layout.getOffsetForHorizontal(line, x);
        return findMetaChipAtOffset(text, offset);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused)
            mFormatBar.setEditText(this);
        else
            mFormatBar.setEditText(null);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (mFormatBar != null)
            mFormatBar.updateFormattingAtCursor();
    }

    public void setFormatBar(TextFormatBar formatBar) {
        mFormatBar = formatBar;
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        mSettingText = true;
        super.setText(text, type);
        mSettingText = false;
    }

    private static class SpanData {
        Object span;
        int start;
        int end;
        int flags;
        boolean extendToCursor;
    }

}
