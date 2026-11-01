package io.mrarm.irc;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spanned;
import android.text.style.StyleSpan;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.util.MessageBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MessageFormatPresetSpacingTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void normalPresetKeepsIndentImmediatelyAfterTime() {
        assertIndentImmediatelyAfterTime(
                MessageFormatSettingsActivity.buildPresetMessageFormat(context, 0, false, false));
        assertIndentImmediatelyAfterTime(
                MessageFormatSettingsActivity.buildPresetMessageFormat(context, 0, false, true));
        assertIndentImmediatelyAfterTime(
                MessageFormatSettingsActivity.buildPresetMessageFormat(context, 1, false, true));
    }

    @Test
    public void actionNoticeAndEventPresetsKeepIndentImmediatelyAfterTime() {
        assertIndentImmediatelyAfterTime(
                MessageFormatSettingsActivity.buildActionPresetMessageFormat(context, 0, false));
        assertIndentImmediatelyAfterTime(
                MessageFormatSettingsActivity.buildNoticePresetMessageFormat(context, 0));
        assertIndentImmediatelyAfterTime(
                MessageFormatSettingsActivity.buildNoticePresetMessageFormat(context, 1));
        assertIndentImmediatelyAfterTime(
                MessageFormatSettingsActivity.buildEventPresetMessageFormat(context, 0));
    }

    @Test
    public void mentionLayoutsBoldMessageBodyOnlyForMentions() {
        for (int layout = 0; layout <= 2; layout++) {
            for (boolean prefix : new boolean[] { false, true }) {
                assertFalse(isMessageBodyBold(MessageFormatSettingsActivity
                        .buildPresetMessageFormat(context, layout, false, prefix)));
                assertTrue(isMessageBodyBold(MessageFormatSettingsActivity
                        .buildPresetMessageFormat(context, layout, true, prefix)));
            }
        }
        for (boolean prefix : new boolean[] { false, true }) {
            assertFalse(isMessageBodyBold(MessageFormatSettingsActivity
                    .buildActionPresetMessageFormat(context, 0, false, prefix)));
            assertTrue(isMessageBodyBold(MessageFormatSettingsActivity
                    .buildActionPresetMessageFormat(context, 0, true, prefix)));
        }
    }

    private boolean isMessageBodyBold(CharSequence format) {
        Spanned text = (Spanned) format;
        MessageBuilder.MetaChipSpan message = findChip(text, MessageBuilder.MetaChipSpan.TYPE_MESSAGE);
        assertNotNull(message);
        int start = text.getSpanStart(message);
        int end = text.getSpanEnd(message);
        for (StyleSpan span : text.getSpans(start, end, StyleSpan.class)) {
            if ((span.getStyle() & Typeface.BOLD) != 0
                    && text.getSpanStart(span) <= start && text.getSpanEnd(span) >= end)
                return true;
        }
        return false;
    }

    private void assertIndentImmediatelyAfterTime(CharSequence format) {
        Spanned spanned = (Spanned) format;
        MessageBuilder.MetaChipSpan time = findChip(spanned, MessageBuilder.MetaChipSpan.TYPE_TIME);
        MessageBuilder.MetaChipSpan indent = findChip(spanned,
                MessageBuilder.MetaChipSpan.TYPE_WRAP_ANCHOR);
        assertNotNull(time);
        assertNotNull(indent);
        assertEquals(spanned.getSpanEnd(time), spanned.getSpanStart(indent));
    }

    private MessageBuilder.MetaChipSpan findChip(Spanned text, int type) {
        for (MessageBuilder.MetaChipSpan chip : text.getSpans(0, text.length(),
                MessageBuilder.MetaChipSpan.class)) {
            if (chip.getType() == type)
                return chip;
        }
        return null;
    }
}
