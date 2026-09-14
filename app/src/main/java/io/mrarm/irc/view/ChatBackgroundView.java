package io.mrarm.irc.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import io.mrarm.irc.config.ChatBackgroundSettings;
import io.mrarm.irc.util.DefaultPreferences;

/** Renders the configured color/image behind channel and server-status message lists. */
public class ChatBackgroundView extends AppCompatImageView
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final Matrix transform = new Matrix();
    private Bitmap bitmap;
    private int lastWidth;
    private int lastHeight;
    private long lastImageModified;
    private long lastImageRevision;

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
                || ChatBackgroundSettings.PREF_SCALE.equals(key)
                || ChatBackgroundSettings.PREF_ZOOM.equals(key)
                || ChatBackgroundSettings.PREF_FOCUS_X.equals(key)
                || ChatBackgroundSettings.PREF_FOCUS_Y.equals(key)
                || ChatBackgroundSettings.PREF_OPACITY.equals(key)
                || ChatBackgroundSettings.PREF_IMAGE_REVISION.equals(key))
            post(this::refresh);
    }

    public void refresh() {
        int themeColor = resolveThemeBackgroundColor();
        int color = ChatBackgroundSettings.hasCustomColor(getContext())
                ? ChatBackgroundSettings.getCustomColor(getContext(), themeColor) : themeColor;
        setBackgroundColor(color);

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
        SharedPreferences preferences = DefaultPreferences.get(getContext());
        long revision = preferences.getLong(ChatBackgroundSettings.PREF_IMAGE_REVISION, 0L);
        long modified = ChatBackgroundSettings.getImageFile(getContext()).lastModified();
        boolean reload = bitmap == null || bitmap.isRecycled() || width != lastWidth
                || height != lastHeight || modified != lastImageModified
                || revision != lastImageRevision;
        if (reload) {
            Bitmap next = ChatBackgroundSettings.decodeSampledImage(getContext(), width, height);
            clearBitmap();
            bitmap = next;
            lastWidth = width;
            lastHeight = height;
            lastImageModified = modified;
            lastImageRevision = revision;
            setImageBitmap(bitmap);
        }
        applyPresentation(width, height);
    }

    private void applyPresentation(int width, int height) {
        setImageAlpha(Math.round(ChatBackgroundSettings.getOpacity(getContext()) * 2.55f));
        String scaleMode = ChatBackgroundSettings.getScale(getContext());
        if (ChatBackgroundSettings.SCALE_MATRIX.equals(scaleMode) && bitmap != null
                && !bitmap.isRecycled()) {
            setScaleType(ScaleType.MATRIX);
            float base = Math.max((float) width / bitmap.getWidth(),
                    (float) height / bitmap.getHeight());
            float scale = base * ChatBackgroundSettings.getZoom(getContext());
            float scaledWidth = bitmap.getWidth() * scale;
            float scaledHeight = bitmap.getHeight() * scale;
            float tx = width * 0.5f
                    - ChatBackgroundSettings.getFocusX(getContext()) * scaledWidth;
            float ty = height * 0.5f
                    - ChatBackgroundSettings.getFocusY(getContext()) * scaledHeight;
            tx = clamp(tx, width - scaledWidth, 0f);
            ty = clamp(ty, height - scaledHeight, 0f);
            transform.reset();
            transform.setScale(scale, scale);
            transform.postTranslate(tx, ty);
            setImageMatrix(transform);
        } else if (ChatBackgroundSettings.SCALE_FIT.equals(scaleMode)) {
            setScaleType(ScaleType.FIT_CENTER);
        } else if (ChatBackgroundSettings.SCALE_STRETCH.equals(scaleMode)) {
            setScaleType(ScaleType.FIT_XY);
        } else {
            setScaleType(ScaleType.CENTER_CROP);
        }
    }

    private int resolveThemeBackgroundColor() {
        TypedValue value = new TypedValue();
        if (!getContext().getTheme().resolveAttribute(android.R.attr.colorBackground, value, true))
            return 0x00000000;
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT)
            return value.data;
        if (value.resourceId != 0)
            return ContextCompat.getColor(getContext(), value.resourceId);
        return value.data;
    }

    private static float clamp(float value, float min, float max) {
        if (min > max)
            return (min + max) * 0.5f;
        return Math.max(min, Math.min(max, value));
    }

    private void clearBitmap() {
        setImageDrawable(null);
        setImageAlpha(255);
        if (bitmap != null && !bitmap.isRecycled())
            bitmap.recycle();
        bitmap = null;
        lastWidth = 0;
        lastHeight = 0;
        lastImageModified = 0;
        lastImageRevision = 0;
    }
}
