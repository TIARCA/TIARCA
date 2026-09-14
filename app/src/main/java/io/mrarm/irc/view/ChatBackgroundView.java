package io.mrarm.irc.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import io.mrarm.irc.R;
import io.mrarm.irc.config.ChatBackgroundSettings;
import io.mrarm.irc.util.DefaultPreferences;

/** Renders the configured color/image behind channel and server-status message lists. */
public class ChatBackgroundView extends AppCompatImageView
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Bitmap bitmap;
    private int lastWidth;
    private int lastHeight;
    private long lastImageModified;

    public ChatBackgroundView(Context context) {
        super(context);
        init();
    }

    public ChatBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ChatBackgroundView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
        setFocusable(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        DefaultPreferences.get(getContext()).registerOnSharedPreferenceChangeListener(this);
        post(this::refresh);
    }

    @Override
    protected void onDetachedFromWindow() {
        DefaultPreferences.get(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        clearBitmap();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh)
            refresh();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (ChatBackgroundSettings.PREF_TYPE.equals(key)
                || ChatBackgroundSettings.PREF_COLOR.equals(key)
                || ChatBackgroundSettings.PREF_SCALE.equals(key))
            post(this::refresh);
    }

    public void refresh() {
        int themeColor = resolveThemeBackgroundColor();
        int color = ChatBackgroundSettings.hasCustomColor(getContext())
                ? ChatBackgroundSettings.getCustomColor(getContext(), themeColor) : themeColor;
        setBackgroundColor(color);

        String scale = ChatBackgroundSettings.getScale(getContext());
        if (ChatBackgroundSettings.SCALE_FIT.equals(scale))
            setScaleType(ScaleType.FIT_CENTER);
        else if (ChatBackgroundSettings.SCALE_STRETCH.equals(scale))
            setScaleType(ScaleType.FIT_XY);
        else
            setScaleType(ScaleType.CENTER_CROP);

        if (!ChatBackgroundSettings.TYPE_IMAGE.equals(
                ChatBackgroundSettings.getType(getContext()))
                || !ChatBackgroundSettings.hasImage(getContext())) {
            clearBitmap();
            return;
        }

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
            height = getResources().getDisplayMetrics().heightPixels;
        }
        long modified = ChatBackgroundSettings.getImageFile(getContext()).lastModified();
        if (bitmap != null && !bitmap.isRecycled() && width == lastWidth && height == lastHeight
                && modified == lastImageModified)
            return;

        Bitmap next = ChatBackgroundSettings.decodeSampledImage(getContext(), width, height);
        clearBitmap();
        bitmap = next;
        lastWidth = width;
        lastHeight = height;
        lastImageModified = modified;
        setImageBitmap(bitmap);
    }

    private int resolveThemeBackgroundColor() {
        TypedValue value = new TypedValue();
        if (!getContext().getTheme().resolveAttribute(R.attr.colorBackground, value, true))
            return 0x00000000;
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT)
            return value.data;
        if (value.resourceId != 0)
            return ContextCompat.getColor(getContext(), value.resourceId);
        return value.data;
    }

    private void clearBitmap() {
        setImageDrawable(null);
        if (bitmap != null && !bitmap.isRecycled())
            bitmap.recycle();
        bitmap = null;
        lastWidth = 0;
        lastHeight = 0;
        lastImageModified = 0;
    }
}
