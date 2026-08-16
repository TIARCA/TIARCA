package io.mrarm.irc;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import io.mrarm.irc.util.theme.ThemeManager;

public class ThemedActivity extends AppCompatActivity implements ThemeManager.ThemeChangeListener {

    private boolean mThemeChanged;
    private int mDarkMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager helper = ThemeManager.getInstance(this);
        helper.addThemeChangeListener(this);
        mDarkMode = AppCompatDelegate.getDefaultNightMode();
        super.onCreate(savedInstanceState);
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

    /** Keeps legacy ActionBar screens below their bar when Android 15+ enforces edge-to-edge. */
    private void applyActionBarContentInset() {
        if (Build.VERSION.SDK_INT < 35 || getSupportActionBar() == null)
            return;
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() != 1)
            return;
        View root = content.getChildAt(0);
        TypedValue actionBarSize = new TypedValue();
        if (!getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize,
                actionBarSize, true))
            return;
        int height = TypedValue.complexToDimensionPixelSize(actionBarSize.data,
                getResources().getDisplayMetrics());
        root.setPadding(root.getPaddingLeft(), root.getPaddingTop() + height,
                root.getPaddingRight(), root.getPaddingBottom());
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
