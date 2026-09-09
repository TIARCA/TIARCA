package io.mrarm.irc.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * RelativeLayout that keeps its content above system bars and the on-screen keyboard.
 *
 * Android 15+ enforces edge-to-edge for apps targeting recent SDKs, so adjustResize alone
 * no longer guarantees that legacy layouts are kept outside the IME. Applying the bottom
 * inset as padding preserves the usable viewport while still allowing child scroll views
 * to scroll their full content above the keyboard.
 */
public class ImeInsetRelativeLayout extends RelativeLayout {

    private final int mBaseBottomPadding;

    public ImeInsetRelativeLayout(Context context) {
        this(context, null);
    }

    public ImeInsetRelativeLayout(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImeInsetRelativeLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mBaseBottomPadding = getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(this, (view, windowInsets) -> {
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottomInset = Math.max(ime.bottom, systemBars.bottom);
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(),
                    mBaseBottomPadding + bottomInset);
            return windowInsets;
        });
    }
}
