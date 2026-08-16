package io.mrarm.irc.view;

import android.content.Context;
import com.google.android.material.navigation.NavigationView;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowInsets;

public class InsetNavigationView extends NavigationView {

    private View mView;
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
