package io.mrarm.irc.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

public class ClipboardUtils {

    private ClipboardUtils() {
    }

    public static String toPlainText(CharSequence cs) {
        if (cs == null) {
            return "";
        }
        return cs.toString();
    }

    public static boolean copyPlainText(Context context, CharSequence label, CharSequence text) {
        if (context == null) {
            return false;
        }
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return false;
        }
        String plainLabel = toPlainText(label);
        String plainText = toPlainText(text);
        ClipData clip = ClipData.newPlainText(plainLabel, plainText);
        clipboard.setPrimaryClip(clip);
        return true;
    }

}
