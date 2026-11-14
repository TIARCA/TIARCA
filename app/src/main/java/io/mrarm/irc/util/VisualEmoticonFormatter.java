package io.mrarm.irc.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Draws phone emoji over IRC emoticons without replacing even one character.
 * The underlying CharSequence, clipboard selection and IRC storage remain
 * exactly as received. Clickable URLs and channel links are never decorated.
 */
public final class VisualEmoticonFormatter {
    private static final Map<String, String> EMOJI = new HashMap<>();
    private static final Pattern EMOTICONS;

    static {
        add("🙂", ":)", "=)", ":-)");
        add("😊", "^_^");
        add("😀", ":D", "=D", ":-D");
        add("😉", ";)", ";-)");
        add("😟", ":(", ":[", "=(", ":-(");
        add("😛", ":p", ":P", "=P", ":-p", ":-P");
        add("😮", ":o", ":O", ":-o", ":-O");
        add("🤓", "8-)", "B-)");
        add("😎", "8|", "B|", "8-|", "B-|");
        add("😠", ">:(", ">:-(");
        add("😡", ">:o", ">:O", ">:-o", ">:-O");
        add("😕", ":/", ":\\", ":-/", ":-\\");
        add("😢", ":'(");
        add("😈", "3:)", "3:-)");
        add("😇", "O:)", "O:-)");
        add("😘", ":*", ":-*");
        add("😑", "-_-", ":|", ":-|");
        add("😳", "o.O", "O.o");
        // No exact Unicode Pac-Man emoji exists; the yellow circle is a fallback.
        add("🟡", ":v");
        add("😺", ":3");
        add("❤️", "<3");
        add("🤖", ":]", ":-]", ":|]");
        add("🐧", "<(\")");
        add("🐬", "(^^^)");
        add("👍", "(y)", "(Y)");
        add("💩", ":poop:");

        List<String> keys = new ArrayList<>(EMOJI.keySet());
        keys.sort(Comparator.comparingInt(String::length).reversed());
        StringJoiner alternatives = new StringJoiner("|");
        for (String key : keys) alternatives.add(Pattern.quote(key));
        EMOTICONS = Pattern.compile(alternatives.toString());
    }

    private VisualEmoticonFormatter() { }

    private static void add(String emoji, String... forms) {
        for (String form : forms) EMOJI.put(form, emoji);
    }

    public static CharSequence decorate(CharSequence linkedText) {
        if (linkedText == null || linkedText.length() == 0) return linkedText;
        SpannableStringBuilder result = new SpannableStringBuilder(linkedText);
        Matcher matcher = EMOTICONS.matcher(result);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (start > 0 && !isBoundary(result.charAt(start - 1))) continue;
            if (end < result.length() && !isBoundary(result.charAt(end))) continue;
            // LinkHelper has already installed spans for real URLs and #channels.
            // Do not draw emoji over a clickable character, even inside link text.
            if (result.getSpans(start, end, ClickableSpan.class).length > 0) continue;
            result.setSpan(new EmojiDrawingSpan(EMOJI.get(matcher.group())),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return result;
    }

    private static boolean isBoundary(char c) {
        return Character.isWhitespace(c) || ".,;!?()[]{}\"'".indexOf(c) >= 0;
    }

    /** ReplacementSpan paints emoji while preserving the exact ASCII source. */
    public static final class EmojiDrawingSpan extends ReplacementSpan {
        private final String emoji;

        private EmojiDrawingSpan(String emoji) {
            this.emoji = emoji;
        }

        public String getEmoji() {
            return emoji;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start,
                           int end, Paint.FontMetricsInt fm) {
            return Math.round(paint.measureText(emoji));
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start,
                         int end, float x, int top, int y, int bottom,
                         @NonNull Paint paint) {
            canvas.drawText(emoji, x, y, paint);
        }
    }
}
