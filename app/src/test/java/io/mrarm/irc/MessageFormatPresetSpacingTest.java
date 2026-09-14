package io.mrarm.irc;

import android.content.Context;
import android.text.Spanned;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.util.MessageBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
