package io.mrarm.irc.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;

import org.junit.Test;

public class VisualEmoticonFormatterTest {

    @Test
    public void originalTextIsPreservedForCopyAndIrc() {
        String raw = "Ciao :) :o :O :| :] :-] :|] :poop:";
        CharSequence decorated = VisualEmoticonFormatter.decorate(raw);
        assertEquals(raw, decorated.toString());
        Spanned spans = (Spanned) decorated;
        assertEquals("🙂", emojiAt(spans, raw.indexOf(":)")));
        assertEquals("😮", emojiAt(spans, raw.indexOf(":o")));
        assertEquals("😮", emojiAt(spans, raw.indexOf(":O")));
        assertEquals("😑", emojiAt(spans, raw.indexOf(":| ")));
        assertEquals("🤖", emojiAt(spans, raw.indexOf(":]")));
        assertEquals("🤖", emojiAt(spans, raw.indexOf(":-]")));
        assertEquals("🤖", emojiAt(spans, raw.indexOf(":|]")));
        assertEquals("💩", emojiAt(spans, raw.indexOf(":poop:")));
    }

    @Test
    public void skipsClickableLinksAndLeavesTheirSpansIntact() {
        String text = "Test :) https://example.org/a:) #canale:)";
        SpannableString linked = new SpannableString(text);
        int webStart = text.indexOf("https://");
        int webEnd = webStart + "https://example.org/a:)".length();
        int channelStart = text.indexOf("#canale");
        linked.setSpan(new URLSpan("https://example.org/a:)"), webStart, webEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        linked.setSpan(new ClickableSpan() {
            @Override public void onClick(View view) { }
        }, channelStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        Spanned decorated = (Spanned) VisualEmoticonFormatter.decorate(linked);
        assertEquals(text, decorated.toString());
        assertEquals("🙂", emojiAt(decorated, text.indexOf(":)")));
        assertEquals(null, emojiAt(decorated, webEnd - 2));
        assertEquals(null, emojiAt(decorated, text.length() - 2));
        assertEquals(2, decorated.getSpans(0, decorated.length(), ClickableSpan.class).length);
    }

    @Test
    public void doesNotConvertEmbeddedWordsMasksOrLinks() {
        String raw = "abc:) :)xyz nick!user@host:) a:poop:z /path/:) :poop:";
        Spanned decorated = (Spanned) VisualEmoticonFormatter.decorate(raw);
        assertEquals(raw, decorated.toString());
        assertEquals(1, decorated.getSpans(
                0, decorated.length(), VisualEmoticonFormatter.EmojiDrawingSpan.class).length);
        assertEquals("💩", emojiAt(decorated, raw.lastIndexOf(":poop:")));
    }

    @Test
    public void allVariantsAreKeptDistinctIncludingRobot() {
        String raw = ":) =) :-) :] :-] :|] :D =D :-D 3:) 3:-) O:) O:-) :v <3";
        Spanned result = (Spanned) VisualEmoticonFormatter.decorate(raw);
        assertEquals(raw, result.toString());
        assertEquals("🙂", emojiAt(result, 0));
        assertEquals("🤖", emojiAt(result, raw.indexOf(":]")));
        assertEquals("🤖", emojiAt(result, raw.indexOf(":-]")));
        assertEquals("🤖", emojiAt(result, raw.indexOf(":|]")));
        assertEquals("😈", emojiAt(result, raw.indexOf("3:)")));
        assertEquals("😇", emojiAt(result, raw.indexOf("O:)")));
        assertEquals("🟡", emojiAt(result, raw.indexOf(":v")));
        assertEquals("❤️", emojiAt(result, raw.indexOf("<3")));
    }

    private static String emojiAt(Spanned spans, int index) {
        VisualEmoticonFormatter.EmojiDrawingSpan[] values = spans.getSpans(index, index + 1,
                VisualEmoticonFormatter.EmojiDrawingSpan.class);
        for (VisualEmoticonFormatter.EmojiDrawingSpan value : values) {
            if (spans.getSpanStart(value) <= index && spans.getSpanEnd(value) > index)
                return value.getEmoji();
        }
        return null;
    }
}
