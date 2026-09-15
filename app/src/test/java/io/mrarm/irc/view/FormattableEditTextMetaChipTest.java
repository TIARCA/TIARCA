package io.mrarm.irc.view;

import android.content.Context;
import android.text.SpannableString;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.R;
import io.mrarm.irc.util.MessageBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class FormattableEditTextMetaChipTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void metaChipLookupSelectsOnlyChipObjectsNotLiteralSeparators() {
        SpannableString text = new SpannableString("\0*\0");
        MessageBuilder.MetaChipSpan time = new MessageBuilder.MetaChipSpan(
                context, MessageBuilder.MetaChipSpan.TYPE_TIME);
        MessageBuilder.MetaChipSpan sender = new MessageBuilder.MetaChipSpan(
                context, MessageBuilder.MetaChipSpan.TYPE_SENDER);
        text.setSpan(time, 0, 1, MessageBuilder.FORMAT_SPAN_FLAGS);
        text.setSpan(sender, 2, 3, MessageBuilder.FORMAT_SPAN_FLAGS);

        assertSame(time, FormattableEditText.findMetaChipAtOffset(text, 0));
        assertNull(FormattableEditText.findMetaChipAtOffset(text, 1));
        assertSame(sender, FormattableEditText.findMetaChipAtOffset(text, 2));
    }

    @Test
    public void advancedMessageEditorsMapToTheirLegacyPresetArrowsOnly() {
        assertEquals(R.id.message_format_normal_preset,
                FormattableEditText.getLegacyPresetButtonId(R.id.message_format_normal));
        assertEquals(R.id.message_format_normal_mention_preset,
                FormattableEditText.getLegacyPresetButtonId(R.id.message_format_normal_mention));
        assertEquals(R.id.message_format_action_preset,
                FormattableEditText.getLegacyPresetButtonId(R.id.message_format_action));
        assertEquals(R.id.message_format_action_mention_preset,
                FormattableEditText.getLegacyPresetButtonId(R.id.message_format_action_mention));
        assertEquals(R.id.message_format_notice_preset,
                FormattableEditText.getLegacyPresetButtonId(R.id.message_format_notice));
        assertEquals(R.id.message_format_event_preset,
                FormattableEditText.getLegacyPresetButtonId(R.id.message_format_event));
        assertEquals(0, FormattableEditText.getLegacyPresetButtonId(R.id.date_format));
    }
}
