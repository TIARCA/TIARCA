package io.mrarm.irc;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import io.mrarm.irc.util.AppCompatViewFactory;
import io.mrarm.irc.util.AppFontManager;
import io.mrarm.irc.util.theme.ThemeManager;

public class ThemedActivity extends AppCompatActivity implements ThemeManager.ThemeChangeListener {

    private boolean mThemeChanged;
    private int mDarkMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (getLayoutInflater().getFactory2() == null)
            getLayoutInflater().setFactory2(new AppCompatViewFactory(this));
        ThemeManager helper = ThemeManager.getInstance(this);
        helper.addThemeChangeListener(this);
        mDarkMode = AppCompatDelegate.getDefaultNightMode();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        AppFontManager.applyToHierarchy(getWindow().getDecorView());
        if (!(this instanceof MainActivity))
            return;

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                MainActivity activity = (MainActivity) ThemedActivity.this;
                View drawerView = findViewById(R.id.drawer_layout);
                boolean drawerOpen = drawerView instanceof DrawerLayout &&
                        (((DrawerLayout) drawerView).isDrawerOpen(GravityCompat.START) ||
                                ((DrawerLayout) drawerView).isDrawerOpen(GravityCompat.END));

                if (!(activity.getCurrentFragment() instanceof ServerListFragment) || drawerOpen) {
                    setEnabled(false);
                    try {
                        getOnBackPressedDispatcher().onBackPressed();
                    } finally {
                        if (!isFinishing())
                            setEnabled(true);
                    }
                    return;
                }

                new AlertDialog.Builder(activity)
                        .setTitle(R.string.exit_confirm_title)
                        .setPositiveButton(R.string.action_disconnect_and_exit, (dialog, which) ->
                                ((IRCApplication) getApplication()).requestExit())
                        .setNeutralButton(R.string.action_stay_in_background, (dialog, which) ->
                                finish())
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        ThemeManager.getInstance(this).removeThemeChangeListener(this);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mThemeChanged)
            recreate();
    }

    public boolean hasThemeChanged() {
        return mThemeChanged;
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        applyActionBarContentInset();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        applyActionBarContentInset();
    }

    /** Keep legacy ActionBar content below the real toolbar, including the status bar inset.
     * Android 15+ edge-to-edge can place the content and the AppCompat ActionBar in overlapping
     * window coordinates. A fixed actionBarSize padding misses the status bar on some devices.
     */
    private void applyActionBarContentInset() {
        if (Build.VERSION.SDK_INT < 35 || getSupportActionBar() == null)
            return;
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0)
            return;
        View root = content.getChildAt(0);
        int originalTopPadding = root.getPaddingTop();

        // Positions are meaningful only after the action bar and content have been laid out.
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive())
                    observer.removeOnPreDrawListener(this);

                View actionBar = findViewById(androidx.appcompat.R.id.action_bar_container);
                if (actionBar == null || actionBar.getVisibility() != View.VISIBLE ||
                        actionBar.getHeight() == 0)
                    return true;

                int[] contentPosition = new int[2];
                int[] actionBarPosition = new int[2];
                root.getLocationInWindow(contentPosition);
                actionBar.getLocationInWindow(actionBarPosition);
                int overlap = actionBarOverlap(contentPosition[1],
                        actionBarPosition[1], actionBar.getHeight());
                int requiredTopPadding = originalTopPadding + overlap;
                if (root.getPaddingTop() != requiredTopPadding)
                    root.setPadding(root.getPaddingLeft(), requiredTopPadding,
                            root.getPaddingRight(), root.getPaddingBottom());
                return true;
            }
        });
    }

    static int actionBarOverlap(int contentTop, int barTop, int barHeight) {
        return Math.max(0, barTop + barHeight - contentTop);
    }

    @Override
    public void setTheme(int resid) {
        ThemeManager helper = ThemeManager.getInstance(this);
        helper.applyThemeToActivity(this);
        super.setTheme(helper.getThemeIdToApply(resid));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        newConfig = new Configuration(newConfig);
        if (AppCompatDelegate.getDefaultNightMode() == mDarkMode) {
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
                newConfig.uiMode &= ~(Configuration.UI_MODE_NIGHT_MASK);
                newConfig.uiMode |= Configuration.UI_MODE_NIGHT_YES;
            }
        }
        ThemeManager helper = ThemeManager.getInstance(this);
        helper.applyThemeToActivity(this);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onThemeChanged() {
        mThemeChanged = true;
    }

}
