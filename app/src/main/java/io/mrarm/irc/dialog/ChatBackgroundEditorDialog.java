package io.mrarm.irc.dialog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import java.io.IOException;

import io.mrarm.irc.R;
import io.mrarm.irc.config.ChatBackgroundSettings;

/** Full-size WYSIWYG editor for a pending chat background image. */
public final class ChatBackgroundEditorDialog {

    private ChatBackgroundEditorDialog() {
    }

    public static void show(Context context, Runnable onApplied, Runnable onCancelled) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int previewWidth = Math.max(1, metrics.widthPixels);
        int previewHeight = Math.max(1, Math.round(metrics.heightPixels * 0.62f));
        Bitmap bitmap = ChatBackgroundSettings.decodeSampledCandidate(
                context, previewWidth, previewHeight);
        if (bitmap == null) {
            ChatBackgroundSettings.discardCandidate(context);
            Toast.makeText(context, R.string.error_file_open, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean matrixMode = ChatBackgroundSettings.SCALE_MATRIX.equals(
                ChatBackgroundSettings.getScale(context));
        float initialZoom = matrixMode ? ChatBackgroundSettings.getZoom(context)
                : ChatBackgroundSettings.DEFAULT_ZOOM;
        float initialFocusX = matrixMode ? ChatBackgroundSettings.getFocusX(context)
                : ChatBackgroundSettings.DEFAULT_FOCUS;
        float initialFocusY = matrixMode ? ChatBackgroundSettings.getFocusY(context)
                : ChatBackgroundSettings.DEFAULT_FOCUS;
        int initialOpacity = ChatBackgroundSettings.getOpacity(context);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 16);
        root.setPadding(padding, padding, padding, 0);

        TextView hint = new TextView(context);
        hint.setText(R.string.chat_background_editor_hint);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        CropView crop = new CropView(context);
        crop.setBitmap(bitmap);
        crop.setTransform(initialZoom, initialFocusX, initialFocusY);
        crop.setOpacity(initialOpacity);
        crop.setBackgroundColor(resolveBackgroundColor(context));
        LinearLayout.LayoutParams cropParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        cropParams.topMargin = dp(context, 10);
        cropParams.bottomMargin = dp(context, 8);
        root.addView(crop, cropParams);

        LinearLayout opacityRow = new LinearLayout(context);
        opacityRow.setOrientation(LinearLayout.HORIZONTAL);
        opacityRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView opacityLabel = new TextView(context);
        opacityLabel.setText(R.string.chat_background_opacity);
        opacityRow.addView(opacityLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView opacityValue = new TextView(context);
        opacityValue.setGravity(Gravity.END);
        opacityRow.addView(opacityValue, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(opacityRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        SeekBar opacity = new SeekBar(context);
        opacity.setMax(100);
        opacity.setProgress(initialOpacity);
        opacityValue.setText(initialOpacity + "%");
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                crop.setOpacity(progress);
                opacityValue.setText(progress + "%");
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        root.addView(opacity, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final boolean[] applied = { false };
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.pref_title_chat_background)
                .setView(root)
                .setNegativeButton(R.string.action_cancel, (d, which) -> {
                    ChatBackgroundSettings.discardCandidate(context);
                    if (onCancelled != null)
                        onCancelled.run();
                })
                .setPositiveButton(R.string.action_ok, null)
                .create();
        dialog.setOnCancelListener(d -> {
            if (!applied[0]) {
                ChatBackgroundSettings.discardCandidate(context);
                if (onCancelled != null)
                    onCancelled.run();
            }
        });
        dialog.setOnDismissListener(d -> bitmap.recycle());
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null)
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    ChatBackgroundSettings.commitCandidate(context, crop.getZoom(),
                            crop.getFocusX(), crop.getFocusY(), opacity.getProgress());
                    applied[0] = true;
                    if (onApplied != null)
                        onApplied.run();
                    dialog.dismiss();
                } catch (IOException e) {
                    Toast.makeText(context, R.string.error_file_open, Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

    private static int resolveBackgroundColor(Context context) {
        TypedValue value = new TypedValue();
        int themeColor = 0xFF000000;
        if (context.getTheme().resolveAttribute(android.R.attr.colorBackground, value, true)) {
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                themeColor = value.data;
            } else if (value.resourceId != 0) {
                themeColor = ContextCompat.getColor(context, value.resourceId);
            }
        }
        return ChatBackgroundSettings.hasCustomColor(context)
                ? ChatBackgroundSettings.getCustomColor(context, themeColor) : themeColor;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Image crop surface expressed as zoom + normalized focal point. */
    static final class CropView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Matrix matrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private Bitmap bitmap;
        private float zoom = ChatBackgroundSettings.DEFAULT_ZOOM;
        private float focusX = ChatBackgroundSettings.DEFAULT_FOCUS;
        private float focusY = ChatBackgroundSettings.DEFAULT_FOCUS;
        private float lastX;
        private float lastY;
        private boolean dragging;

        CropView(Context context) {
            super(context);
            setMinimumHeight(Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.5f));
            scaleDetector = new ScaleGestureDetector(context,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override
                        public boolean onScale(ScaleGestureDetector detector) {
                            zoomAround(detector.getFocusX(), detector.getFocusY(),
                                    detector.getScaleFactor());
                            return true;
                        }
                    });
        }

        void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            invalidate();
        }

        void setTransform(float zoom, float focusX, float focusY) {
            this.zoom = clamp(zoom, ChatBackgroundSettings.DEFAULT_ZOOM,
                    ChatBackgroundSettings.MAX_ZOOM);
            this.focusX = clamp01(focusX);
            this.focusY = clamp01(focusY);
            invalidate();
        }

        void setOpacity(int opacity) {
            paint.setAlpha(Math.round(Math.max(0, Math.min(100, opacity)) * 2.55f));
            invalidate();
        }

        float getZoom() { return zoom; }
        float getFocusX() { return focusX; }
        float getFocusY() { return focusY; }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null || bitmap.isRecycled() || getWidth() <= 0 || getHeight() <= 0)
                return;
            Geometry g = geometry(zoom, focusX, focusY);
            matrix.reset();
            matrix.setScale(g.scale, g.scale);
            matrix.postTranslate(g.tx, g.ty);
            canvas.drawBitmap(bitmap, matrix, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging && !scaleDetector.isInProgress()) {
                        float x = event.getX();
                        float y = event.getY();
                        pan(x - lastX, y - lastY);
                        lastX = x;
                        lastY = y;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return true;
            }
        }

        private void pan(float dx, float dy) {
            if (bitmap == null || getWidth() <= 0 || getHeight() <= 0)
                return;
            Geometry g = geometry(zoom, focusX, focusY);
            float tx = clamp(g.tx + dx, getWidth() - g.scaledWidth, 0f);
            float ty = clamp(g.ty + dy, getHeight() - g.scaledHeight, 0f);
            focusX = clamp01((getWidth() * 0.5f - tx) / g.scaledWidth);
            focusY = clamp01((getHeight() * 0.5f - ty) / g.scaledHeight);
            invalidate();
        }

        private void zoomAround(float px, float py, float factor) {
            if (bitmap == null || getWidth() <= 0 || getHeight() <= 0)
                return;
            Geometry before = geometry(zoom, focusX, focusY);
            float imageX = clamp01((px - before.tx) / before.scaledWidth);
            float imageY = clamp01((py - before.ty) / before.scaledHeight);
            float nextZoom = clamp(zoom * factor, ChatBackgroundSettings.DEFAULT_ZOOM,
                    ChatBackgroundSettings.MAX_ZOOM);
            Geometry centered = geometry(nextZoom, focusX, focusY);
            float tx = clamp(px - imageX * centered.scaledWidth,
                    getWidth() - centered.scaledWidth, 0f);
            float ty = clamp(py - imageY * centered.scaledHeight,
                    getHeight() - centered.scaledHeight, 0f);
            zoom = nextZoom;
            focusX = clamp01((getWidth() * 0.5f - tx) / centered.scaledWidth);
            focusY = clamp01((getHeight() * 0.5f - ty) / centered.scaledHeight);
            invalidate();
        }

        private Geometry geometry(float zoom, float focusX, float focusY) {
            float width = Math.max(1, getWidth());
            float height = Math.max(1, getHeight());
            float base = Math.max(width / bitmap.getWidth(), height / bitmap.getHeight());
            float scale = base * zoom;
            float scaledWidth = bitmap.getWidth() * scale;
            float scaledHeight = bitmap.getHeight() * scale;
            float tx = width * 0.5f - focusX * scaledWidth;
            float ty = height * 0.5f - focusY * scaledHeight;
            tx = clamp(tx, width - scaledWidth, 0f);
            ty = clamp(ty, height - scaledHeight, 0f);
            return new Geometry(scale, scaledWidth, scaledHeight, tx, ty);
        }

        private static float clamp01(float value) {
            return clamp(value, 0f, 1f);
        }

        private static float clamp(float value, float min, float max) {
            if (min > max)
                return (min + max) * 0.5f;
            return Math.max(min, Math.min(max, value));
        }

        private static final class Geometry {
            final float scale;
            final float scaledWidth;
            final float scaledHeight;
            final float tx;
            final float ty;

            Geometry(float scale, float scaledWidth, float scaledHeight, float tx, float ty) {
                this.scale = scale;
                this.scaledWidth = scaledWidth;
                this.scaledHeight = scaledHeight;
                this.tx = tx;
                this.ty = ty;
            }
        }
    }
}
