package io.mrarm.irc.util;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Lightweight fallback avatar derived from the nickname and its chat colour. */
public final class InitialAvatarDrawable extends Drawable {
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String initial;

    public InitialAvatarDrawable(String nickname, int color) {
        background.setColor(color);
        text.setColor(readableTextColor(color));
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        initial = nickname == null || nickname.trim().isEmpty() ? "?" :
                nickname.trim().substring(0, 1).toUpperCase(java.util.Locale.getDefault());
    }

    @Override public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float radius = Math.min(bounds.width(), bounds.height()) / 2f;
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, background);
        text.setTextSize(radius);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = bounds.exactCenterY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(initial, bounds.exactCenterX(), baseline, text);
    }

    private static int readableTextColor(int color) {
        double luminance = .2126 * Color.red(color) + .7152 * Color.green(color) +
                .0722 * Color.blue(color);
        return luminance > 150 ? Color.BLACK : Color.WHITE;
    }

    @Override public void setAlpha(int alpha) { background.setAlpha(alpha); text.setAlpha(alpha); }
    @Override public void setColorFilter(@Nullable ColorFilter filter) {
        background.setColorFilter(filter); text.setColorFilter(filter);
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
