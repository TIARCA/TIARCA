package io.mrarm.irc.util;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ClipboardUtilsTest {

    @Test
    public void testToPlainTextNull() {
        Assert.assertEquals("", ClipboardUtils.toPlainText(null));
    }

    @Test
    public void testToPlainTextNormalString() {
        String text = "Hello World!";
        Assert.assertEquals(text, ClipboardUtils.toPlainText(text));
    }

    @Test
    public void testToPlainTextSpannableStringWithStyleSpans() {
        SpannableString spannable = new SpannableString("Styled text with red and bold");
        spannable.setSpan(new ForegroundColorSpan(0xFFFF0000), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), 17, 20, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        String result = ClipboardUtils.toPlainText(spannable);

        Assert.assertEquals("Styled text with red and bold", result);
        Assert.assertEquals(String.class, result.getClass());
    }

    @Test
    public void testToPlainTextWithUrlSpans() {
        SpannableString spannable = new SpannableString("Visit https://example.com/test for info");
        spannable.setSpan(new URLSpan("https://example.com/test"), 6, 30, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        String result = ClipboardUtils.toPlainText(spannable);

        Assert.assertEquals("Visit https://example.com/test for info", result);
        Assert.assertEquals(String.class, result.getClass());
    }

    @Test
    public void testToPlainTextUnicodeAndEmojis() {
        String emojiString = "Nick: 🎉\uD83D\uDE00 Test αβγ 123";
        SpannableString spannable = new SpannableString(emojiString);
        spannable.setSpan(new StyleSpan(Typeface.ITALIC), 0, emojiString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        String result = ClipboardUtils.toPlainText(spannable);

        Assert.assertEquals(emojiString, result);
        Assert.assertEquals(String.class, result.getClass());
    }

    @Test
    public void testToPlainTextPreservesNewlinesAndTabs() {
        String multiline = "Line 1\nLine 2\r\n\tTabbed line";
        SpannableString spannable = new SpannableString(multiline);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        String result = ClipboardUtils.toPlainText(spannable);

        Assert.assertEquals(multiline, result);
        Assert.assertEquals(String.class, result.getClass());
    }

    @Test
    public void testToPlainTextIrcMasks() {
        String mask = "*!*@host.example.org";
        SpannableString spannable = new SpannableString(mask);
        spannable.setSpan(new ForegroundColorSpan(0xFF00FF00), 0, mask.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        String result = ClipboardUtils.toPlainText(spannable);

        Assert.assertEquals("*!*@host.example.org", result);
        Assert.assertEquals(String.class, result.getClass());
    }
}
