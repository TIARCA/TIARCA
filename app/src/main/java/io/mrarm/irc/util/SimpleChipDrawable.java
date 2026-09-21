package io.mrarm.irc.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import io.mrarm.irc.R;

public class SimpleChipDrawable extends Drawable {

    private final String mText;
    private final Paint mPaint;
    private final Drawable mBackground;
    private final Drawable mContentDrawable;
    private int mTextWidth;
    private int mTextHeight;
    private int mDefaultTextColor;
    private Rect mTempRect = new Rect();

    public SimpleChipDrawable(Context ctx, String text, Drawable content, boolean transparent) {
        this(ctx, text, content, transparent, false);
    }

    /**
     * @param editorControl true only for message-format meta chips. Those are editor controls and
     *                      intentionally use a fixed, high-contrast pill palette independent of
     *                      chat-message styling or generic ChipsEditText chips elsewhere.
     */
    public SimpleChipDrawable(Context ctx, String text, Drawable content, boolean transparent,
                              boolean editorControl) {
        // The only icon-only meta chip is the message wrap anchor. Render it as an explicit label
        // in the editor so users can understand what it does instead of seeing an unexplained icon.
        if (text == null && content != null) {
            mText = ctx.getString(R.string.message_format_indent_chip);
            mContentDrawable = null;
        } else {
            mText = text;
            mContentDrawable = content;
        }
        StyledAttributesHelper ta = StyledAttributesHelper.obtainStyledAttributes(ctx,
                new int[] { android.R.attr.textAppearance });
        int resId = ta.getResourceId(android.R.attr.textAppearance, 0);
        ta.recycle();
        ta = StyledAttributesHelper.obtainStyledAttributes(ctx, resId,
                new int[] { android.R.attr.textSize, android.R.attr.textColor });
        int textSize = ta.getDimensionPixelSize(android.R.attr.textSize, 0);
        ta.recycle();

        if (editorControl) {
            mDefaultTextColor = ContextCompat.getColor(
                    ctx, R.color.messageFormatEditorChipText);
            mBackground = ContextCompat.getDrawable(
                    ctx, R.drawable.message_format_editor_chip_background);
        } else {
            // Generic chips keep their historical theme-aware colours.
            mDefaultTextColor = StyledAttributesHelper.getColor(
                    ctx, android.R.attr.textColorPrimary, Color.BLACK);
            mBackground = ContextCompat.getDrawable(ctx,
                    transparent ? R.drawable.transparent_chip_background : R.drawable.chip_background);
        }

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTextSize(textSize);
        mPaint.setColor(mDefaultTextColor);
        if (mText != null)
            mTextWidth = (int) mPaint.measureText(mText);
        mTextHeight = (int) (mPaint.descent() - mPaint.ascent());
    }

    public SimpleChipDrawable(Context ctx, String text, boolean transparent) {
        this(ctx, text, null, transparent, false);
    }

    public Paint getPaint() {
        return mPaint;
    }

    public void setDefaultTextColor() {
        getPaint().setColor(mDefaultTextColor);
    }

    @Override
    public int getIntrinsicWidth() {
        mBackground.getPadding(mTempRect);
        return mBackground.getMinimumWidth() + mTempRect.left + mTempRect.right +
                Math.max(mTextWidth,
                        (mContentDrawable != null ? mContentDrawable.getIntrinsicWidth() : 0));
    }

    @Override
    public int getIntrinsicHeight() {
        mBackground.getPadding(mTempRect);
        return mBackground.getMinimumHeight() + mTempRect.top + mTempRect.bottom +
                Math.max(mTextHeight,
                        (mContentDrawable != null ? mContentDrawable.getIntrinsicHeight() : 0));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        mBackground.draw(canvas);
        if (mText != null)
            canvas.drawText(mText, bounds.centerX(),
                    bounds.centerY() - (mPaint.descent() + mPaint.ascent()) / 2, mPaint);
        if (mContentDrawable != null) {
            int cw = mContentDrawable.getIntrinsicWidth();
            int ch = mContentDrawable.getIntrinsicHeight();
            int cx = bounds.centerX() - cw / 2;
            int cy = bounds.centerY() - ch / 2;
            mContentDrawable.setBounds(cx, cy, cx + cw, cy + ch);
            mContentDrawable.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mBackground.setAlpha(alpha);
        if (mPaint != null)
            mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mBackground.setColorFilter(colorFilter);
        if (mPaint != null)
            mPaint.setColorFilter(colorFilter);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        mBackground.setBounds(left, top, right, bottom);
        super.setBounds(left, top, right, bottom);
    }

}
