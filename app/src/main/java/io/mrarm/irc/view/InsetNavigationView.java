package io.mrarm.irc.view;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import androidx.annotation.NonNull;
import androidx.activity.BackEventCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import com.google.android.material.navigation.NavigationView;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowInsets;

public class InsetNavigationView extends NavigationView {

    private View mView;
    private boolean mImeVisibleAtBackStart;
    private int basePaddingTop;
    private int basePaddingBottom;

    public InsetNavigationView(Context context) {
        this(context, null);
    }

    public InsetNavigationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InsetNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        if (child instanceof RecyclerView) {
            mView = child;
            basePaddingTop = mView.getPaddingTop();
            basePaddingBottom = mView.getPaddingBottom();
        }
    }

    // NavigationView registers its own high-priority predictive Back callback while a
    // drawer is open. MainActivity's OnBackPressedCallback never sees those gestures.
    // Keep the member drawer open while Back is being used to dismiss the keyboard.
    @Override
    public void startBackProgress(@NonNull BackEventCompat backEvent) {
        mImeVisibleAtBackStart = isImeVisible();
        if (!mImeVisibleAtBackStart)
            super.startBackProgress(backEvent);
    }

    @Override
    public void updateBackProgress(@NonNull BackEventCompat backEvent) {
        if (!mImeVisibleAtBackStart)
            super.updateBackProgress(backEvent);
    }

    @Override
    public void cancelBackProgress() {
        if (!mImeVisibleAtBackStart)
            super.cancelBackProgress();
        mImeVisibleAtBackStart = false;
    }

    @Override
    public void handleBackInvoked() {
        boolean hideKeyboard = mImeVisibleAtBackStart || isImeVisible();
        mImeVisibleAtBackStart = false;
        if (hideKeyboard) {
            hideIme();
            return;
        }
        super.handleBackInvoked();
    }

    protected boolean isImeVisible() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        return insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
    }

    protected void hideIme() {
        // NavigationView uses an AppCompat/Material-wrapped Activity context.
        // WindowCompat replaces the deprecated ViewCompat controller lookup.
        Context context = getContext();
        while (context instanceof ContextWrapper && !(context instanceof Activity))
            context = ((ContextWrapper) context).getBaseContext();
        if (context instanceof Activity)
            WindowCompat.getInsetsController(((Activity) context).getWindow(), this)
                    .hide(WindowInsetsCompat.Type.ime());
    }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        Insets systemBars = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
                .getInsets(WindowInsetsCompat.Type.systemBars());
        if (mView != null)
            mView.setPadding(systemBars.left,
                    systemBars.top + basePaddingTop,
                    systemBars.right,
                    systemBars.bottom + basePaddingBottom);
        return super.dispatchApplyWindowInsets(insets);
    }

}
