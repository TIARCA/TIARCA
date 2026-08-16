package io.mrarm.irc.view;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.TypedArray;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import io.mrarm.irc.R;

public class OpaqueStatusBarRelativeLayout extends RelativeLayout {

    private Drawable mInsetDrawable;
    private int mTopInset;
    private Rect mTempRect = new Rect();

    public OpaqueStatusBarRelativeLayout(Context context) {
        this(context, null);
    }

    public OpaqueStatusBarRelativeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OpaqueStatusBarRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray ta = context.obtainStyledAttributes(
                attrs, R.styleable.OpaqueStatusBarRelativeLayout, defStyleAttr, 0);
        mInsetDrawable =
                ta.getDrawable(R.styleable.OpaqueStatusBarRelativeLayout_colorPrimaryDark);
        ta.recycle();

        setWillNotDraw(false);

        ViewCompat.setOnApplyWindowInsetsListener(this, (View v, WindowInsetsCompat insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            mTopInset = systemBars.top;
            setPadding(systemBars.left, mTopInset, systemBars.right, systemBars.bottom);
            ViewCompat.postInvalidateOnAnimation(this);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Activity activity = findActivity(getContext());
        if (activity != null)
            WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        ViewCompat.requestApplyInsets(this);
    }

    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity)
                return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public void setInsetColor(int color) {
        mInsetDrawable = new ColorDrawable(color);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mTopInset != 0) {
            int width = getWidth();

            canvas.save();
            canvas.translate(getScrollX(), getScrollY());

            mTempRect.set(0, 0, width, mTopInset);
            if (mInsetDrawable != null) {
                mInsetDrawable.setBounds(mTempRect);
                mInsetDrawable.draw(canvas);
            }

            canvas.restore();
        }
    }
}
