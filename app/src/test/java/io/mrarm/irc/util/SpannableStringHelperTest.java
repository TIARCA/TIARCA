package io.mrarm.irc.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import com.google.gson.JsonObject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SpannableStringHelperTest {

    @Test
    public void adjacentEqualSpansAreMerged() {
        SpannableString text = new SpannableString("sender");
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        SpannableStringHelper.setAndMergeSpans(text, new StyleSpan(Typeface.BOLD), 3, 6,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        StyleSpan[] spans = text.getSpans(0, text.length(), StyleSpan.class);
        assertEquals(1, spans.length);
        assertEquals(0, text.getSpanStart(spans[0]));
        assertEquals(6, text.getSpanEnd(spans[0]));
    }

    @Test
    public void underlineRoundTripsThroughJson() {
        JsonObject json = SpannableStringHelper.spanToJson(new UnderlineSpan());
        Object restored = SpannableStringHelper.spanFromJson(json);

        assertEquals(SpannableStringHelper.SPAN_TYPE_UNDERLINE,
                json.get("type").getAsString());
        assertTrue(restored instanceof UnderlineSpan);
    }
}
