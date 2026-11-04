package io.mrarm.irc.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.cardview.widget.CardView;

/**
 * Handles horizontal alert dismissal, including gestures starting on a child text/button.
 * The parent displays a "Close" indicator behind the card while it is being dragged.
 */
public class SwipeDismissCardView extends CardView {
    public interface OnSwipeListener {
        void onSwipeProgress(float distance);
        void onSwipeDismissed();
    }

    private final int mTouchSlop;
    private float mDownX;
    private float mDownY;
    private boolean mSwiping;
    private OnSwipeListener mListener;

    public SwipeDismissCardView(Context context) {
        this(context, null);
    }

    public SwipeDismissCardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeDismissCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setOnSwipeListener(OnSwipeListener listener) {
        mListener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getRawX();
                mDownY = event.getRawY();
                mSwiping = false;
                if (getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - mDownX;
                float dy = event.getRawY() - mDownY;
                if (mSwiping || (Math.abs(dx) > mTouchSlop &&
                        Math.abs(dx) > Math.abs(dy))) {
                    mSwiping = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getRawX();
                mDownY = event.getRawY();
                mSwiping = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - mDownX;
                if (!mSwiping && Math.abs(dx) > mTouchSlop &&
                        Math.abs(dx) > Math.abs(event.getRawY() - mDownY))
                    mSwiping = true;
                if (mSwiping) {
                    setTranslationX(dx);
                    setAlpha(1f - 0.25f * Math.min(1f,
                            Math.abs(dx) / Math.max(1, getWidth())));
                    if (mListener != null)
                        mListener.onSwipeProgress(dx);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(false);
                if (mSwiping) {
                    boolean dismiss = isPastDismissThreshold(event.getRawX() - mDownX, getWidth());
                    resetSwipe();
                    if (dismiss && mListener != null)
                        mListener.onSwipeDismissed();
                    return true;
                }
                return performClick();
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(false);
                resetSwipe();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    static boolean isPastDismissThreshold(float distance, int width) {
        return width > 0 && Math.abs(distance) >= width * 0.28f;
    }

    public void resetSwipe() {
        mSwiping = false;
        setTranslationX(0f);
        setAlpha(1f);
        if (mListener != null)
            mListener.onSwipeProgress(0f);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
