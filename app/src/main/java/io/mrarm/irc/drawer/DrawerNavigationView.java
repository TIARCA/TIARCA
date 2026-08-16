package io.mrarm.irc.drawer;

import android.content.Context;
import com.google.android.material.navigation.NavigationView;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.WindowInsets;

import io.mrarm.irc.R;

public class DrawerNavigationView extends NavigationView {

    private RecyclerView mNavList;

    public DrawerNavigationView(Context context) {
        this(context, null);
    }

    public DrawerNavigationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DrawerNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        if (mNavList == null)
            mNavList = findViewById(R.id.nav_list);
        Insets systemBars = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
                .getInsets(WindowInsetsCompat.Type.systemBars());
        if (mNavList != null) {
            mNavList.setPadding(systemBars.left,
                    0,
                    systemBars.right,
                    systemBars.bottom);
            if (mNavList.getAdapter() instanceof DrawerMenuListAdapter)
                ((DrawerMenuListAdapter) mNavList.getAdapter()).setHeaderPaddingTop(systemBars.top);
        }
        return super.dispatchApplyWindowInsets(insets);
    }

}
