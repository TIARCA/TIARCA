package io.mrarm.irc.util;

import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import io.mrarm.irc.R;

/**
 * Temporary overlay on a message's TextView during a nickname swipe.
 * It never changes the message spans, layout or touch dispatch.
 */
final class NicknameSwipeFeedbackDrawable extends Drawable {
    private final TextView widget;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final int accentColor;
    private final int foregroundColor;

    private Spannable buffer;
    private ClickableSpan nicknameSpan;
    private String action = "";
    private String label = "";
    private Drawable actionIcon;
    private boolean right;
    private float travel;
    private float progress;

    NicknameSwipeFeedbackDrawable(TextView widget) {
        this.widget = widget;
        TypedArray colors = widget.getContext().obtainStyledAttributes(
                new int[] { R.attr.colorAccent });
        try {
            accentColor = ColorUtils.setAlphaComponent(
                    colors.getColor(0, widget.getCurrentTextColor()), 255);
        } finally {
            colors.recycle();
        }
        foregroundColor = ColorUtils.calculateContrast(Color.WHITE, accentColor) >=
                ColorUtils.calculateContrast(Color.BLACK, accentColor)
                ? Color.WHITE : Color.BLACK;
        labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        labelPaint.setTextSize(widget.getTextSize() * 0.72f);
        labelPaint.setColor(foregroundColor);
    }

    void update(Spannable text, ClickableSpan span, String newAction,
                boolean swipingRight, float distance, float fraction) {
        buffer = text;
        nicknameSpan = span;
        right = swipingRight;
        travel = Math.abs(distance);
        progress = Math.max(0f, Math.min(1f, fraction));
        if (!action.equals(newAction)) {
            action = newAction;
            label = resolveLabel(newAction);
            int iconId = getActionIcon(newAction);
            actionIcon = ContextCompat.getDrawable(widget.getContext(), iconId);
            if (actionIcon != null)
                actionIcon = actionIcon.mutate();
        }
        setBounds(0, 0, widget.getWidth(), widget.getHeight());
        invalidateSelf();
        widget.invalidate();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Layout layout = widget.getLayout();
        if (layout == null || buffer == null || nicknameSpan == null || getBounds().isEmpty())
            return;

        int start = buffer.getSpanStart(nicknameSpan);
        int end = buffer.getSpanEnd(nicknameSpan);
        if (start < 0 || end <= start || start >= layout.getText().length())
            return;

        int line = layout.getLineForOffset(start);
        int lineEnd = Math.min(end, layout.getLineEnd(line));
        float firstX = layout.getPrimaryHorizontal(start);
        float lastX = layout.getPrimaryHorizontal(lineEnd);
        float left = widget.getTotalPaddingLeft() + Math.min(firstX, lastX) -
                widget.getScrollX();
        float rightEdge = widget.getTotalPaddingLeft() + Math.max(firstX, lastX) -
                widget.getScrollX();
        float top = widget.getTotalPaddingTop() + layout.getLineTop(line) -
                widget.getScrollY();
        float bottom = widget.getTotalPaddingTop() + layout.getLineBottom(line) -
                widget.getScrollY();

        // Keep the original nickname text and colours intact.
        backgroundPaint.setColor(ColorUtils.setAlphaComponent(accentColor,
                32 + (int) (54 * progress)));
        float highlightPadding = dp(2);
        canvas.drawRoundRect(new RectF(left - highlightPadding, top,
                rightEdge + highlightPadding, bottom), dp(4), dp(4), backgroundPaint);

        float iconSize = dp(15);
        float textGap = dp(5);
        float horizontalPadding = dp(8);
        float maxWidth = Math.max(dp(1), Math.min(dp(168), getBounds().width() - dp(8)));
        float labelWidth = Math.max(0, maxWidth - iconSize - textGap -
                2 * horizontalPadding);
        String visibleLabel = TextUtils.ellipsize(label, labelPaint, labelWidth,
                TextUtils.TruncateAt.END).toString();
        float width = Math.min(maxWidth, iconSize + textGap +
                labelPaint.measureText(visibleLabel) + 2 * horizontalPadding);
        float height = Math.min(dp(27), getBounds().height());
        if (height <= 0)
            return;

        // The action chip follows the drag slightly, but never moves the chat row.
        float pull = Math.min(dp(8), travel * 0.1f);
        float chipLeft = right ? rightEdge + dp(5) + pull :
                left - width - dp(5) - pull;
        chipLeft = Math.max(dp(2), Math.min(chipLeft,
                getBounds().width() - width - dp(2)));
        float chipTop = Math.max(0, Math.min(top + (bottom - top - height) / 2f,
                getBounds().height() - height));
        RectF chip = new RectF(chipLeft, chipTop, chipLeft + width, chipTop + height);

        // The chip becomes fully opaque once the action's trigger distance is reached.
        int opacity = 150 + (int) (105 * progress);
        backgroundPaint.setColor(ColorUtils.setAlphaComponent(accentColor, opacity));
        canvas.drawRoundRect(chip, height / 2f, height / 2f, backgroundPaint);

        float iconLeft = chip.left + horizontalPadding;
        float iconTop = chip.centerY() - iconSize / 2f;
        if (actionIcon != null) {
            actionIcon.setTint(foregroundColor);
            actionIcon.setAlpha(opacity);
            actionIcon.setBounds(Math.round(iconLeft), Math.round(iconTop),
                    Math.round(iconLeft + iconSize), Math.round(iconTop + iconSize));
            actionIcon.draw(canvas);
        }
        labelPaint.setAlpha(opacity);
        float baseline = chip.centerY() -
                (labelPaint.ascent() + labelPaint.descent()) / 2f;
        canvas.drawText(visibleLabel, iconLeft + iconSize + textGap, baseline, labelPaint);
        labelPaint.setAlpha(255);
    }

    private String resolveLabel(String requestedAction) {
        String[] values = widget.getResources().getStringArray(
                R.array.pref_entry_values_nick_swipe_channel_action);
        String[] entries = widget.getResources().getStringArray(
                R.array.pref_entries_nick_swipe_channel_action);
        for (int i = 0; i < values.length && i < entries.length; i++) {
            if (values[i].equals(requestedAction)) {
                String entry = entries[i];
                int explanatorySuffix = entry.indexOf(" (");
                return explanatorySuffix < 0 ? entry : entry.substring(0, explanatorySuffix);
            }
        }
        return requestedAction;
    }

    private static int getActionIcon(String action) {
        switch (action) {
            case "mention":
                return R.drawable.ic_reply;
            case "whois":
                return R.drawable.ic_info;
            case "private":
                return R.drawable.ic_message;
            case "copy":
                return R.drawable.ic_content_copy;
            case "ignore":
                return R.drawable.ic_block;
            case "monitor":
                return R.drawable.ic_notifications;
            default:
                return R.drawable.ic_info;
        }
    }

    private float dp(float value) {
        return value * widget.getResources().getDisplayMetrics().density;
    }

    @Override
    public void setAlpha(int alpha) {
        // This drawable is only displayed during a live gesture.
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        // Colours follow the current theme's accent.
    }

    // Drawable still requires this override, although Android deprecated the API.
    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
