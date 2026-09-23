package io.mrarm.irc.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import android.view.MotionEvent;
import android.view.View;

import io.mrarm.irc.util.StyledAttributesHelper;

public class TextSelectionHandleView extends View {

    // Android positions the logical selection point slightly above the handle itself while it is
    // dragged. Feeding the handle's top edge directly back into TextView makes multi-line
    // selections cross a line boundary too early.
    private static final float TOUCH_OFFSET_Y_FRACTION = -0.3f;

    public static Drawable getDrawable(Context context, boolean rightHandle) {
        int resId = StyledAttributesHelper.getResourceId(context, rightHandle ?
                android.R.attr.textSelectHandleRight : android.R.attr.textSelectHandleLeft, -1);
        return ContextCompat.getDrawable(context, resId);
    }


    private Drawable mDrawable;
    private int mHotspotX;
    private float mMoveOffsetX;
    private float mMoveOffsetY;
    private MoveListener mMoveListener;

    public TextSelectionHandleView(Context context, Drawable drawable, int hotspotX) {
        super(context);
        mDrawable = drawable;
        mHotspotX = hotspotX;
        setFocusableInTouchMode(true);
    }

    public TextSelectionHandleView(Context context, Drawable drawable, boolean rightHandle) {
        super(context);
        mDrawable = drawable;
        int width = drawable.getIntrinsicWidth();
        mHotspotX = rightHandle ? width / 4 : width * 3 / 4;
        setFocusableInTouchMode(true);
    }

    public TextSelectionHandleView(Context context, boolean rightHandle) {
        this(context, getDrawable(context, rightHandle), rightHandle);
    }

    public int getHotspotX() {
        return mHotspotX;
    }

    public void setOnMoveListener(MoveListener listener) {
        mMoveListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mDrawable.getIntrinsicWidth(), mDrawable.getIntrinsicHeight());
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        mDrawable.setBounds(0, 0, getWidth(), getHeight());
        mDrawable.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mMoveOffsetX = mHotspotX - event.getX();
                mMoveOffsetY = -event.getY() +
                        TOUCH_OFFSET_Y_FRACTION * mDrawable.getIntrinsicHeight();
                if (mMoveListener != null)
                    mMoveListener.onMoveStarted();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mMoveListener != null)
                    mMoveListener.onMoved(event.getRawX() + mMoveOffsetX,
                            event.getRawY() + mMoveOffsetY);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mMoveListener != null)
                    mMoveListener.onMoveFinished();
                if (event.getActionMasked() == MotionEvent.ACTION_UP)
                    performClick();
                return true;
        }
        return false;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public interface MoveListener {

        void onMoveStarted();

        void onMoveFinished();

        void onMoved(float x, float y);

    }

}
