package io.mrarm.irc.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import io.mrarm.irc.R;

/** A small dependency-free circular image view suitable for member avatars. */
public class CircularImageView extends AppCompatImageView {

    private final Path clipPath = new Path();
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean awayIndicatorVisible;

    public CircularImageView(Context context) {
        super(context);
        init();
    }

    public CircularImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircularImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setScaleType(ScaleType.CENTER_CROP);
    }

    /** Show a small orange status dot over the avatar when the user is AWAY. */
    public void setAwayIndicatorVisible(boolean visible) {
        if (awayIndicatorVisible == visible)
            return;
        awayIndicatorVisible = visible;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clipPath.reset();
        clipPath.addCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(clipPath);
        super.onDraw(canvas);
        canvas.restoreToCount(save);

        // Draw outside the avatar clip so the badge remains circular at the bottom-right edge.
        if (awayIndicatorVisible && getWidth() > 0 && getHeight() > 0) {
            float size = Math.min(getWidth(), getHeight());
            float radius = size * 0.11f;
            float outline = size * 0.035f;
            float x = getWidth() - radius - outline;
            float y = getHeight() - radius - outline;
            indicatorPaint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, radius + outline, indicatorPaint);
            indicatorPaint.setColor(ContextCompat.getColor(getContext(), R.color.userAwayColorPrimary));
            canvas.drawCircle(x, y, radius, indicatorPaint);
        }
    }
}
