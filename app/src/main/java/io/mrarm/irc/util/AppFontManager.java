package io.mrarm.irc.util;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.mrarm.irc.config.ChatSettings;

/** Applies the selected chat font to normal application UI text when explicitly enabled. */
public final class AppFontManager {

    private AppFontManager() {
    }

    public static void applyToView(View view) {
        if (!ChatSettings.isGlobalFontEnabled() || !(view instanceof TextView))
            return;

        TextView textView = (TextView) view;
        Typeface target = ChatSettings.getFont();
        Typeface current = textView.getTypeface();

        // Keep intentionally monospaced technical/password fields unchanged unless the selected
        // global font is itself monospace.
        if (!isMonospace(target) && isMonospace(current))
            return;

        int style = current == null ? Typeface.NORMAL : current.getStyle();
        textView.setTypeface(target, style);
    }

    public static void applyToHierarchy(View root) {
        if (!ChatSettings.isGlobalFontEnabled() || root == null)
            return;
        applyToView(root);
        if (!(root instanceof ViewGroup))
            return;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++)
            applyToHierarchy(group.getChildAt(i));
    }

    static boolean isMonospace(Typeface typeface) {
        if (typeface == null)
            return false;
        if (typeface == Typeface.MONOSPACE || typeface.equals(Typeface.MONOSPACE))
            return true;

        // Styling a Typeface (for example MONOSPACE + BOLD) creates a distinct Typeface object.
        // Measuring glyph advances also covers those variants and imported monospace fonts.
        Paint paint = new Paint();
        paint.setTypeface(typeface);
        float narrow = paint.measureText("i");
        float wide = paint.measureText("W");
        float digit = paint.measureText("0");
        return Math.abs(narrow - wide) < 0.01f && Math.abs(narrow - digit) < 0.01f;
    }
}
