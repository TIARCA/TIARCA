package io.mrarm.irc.view;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.R;
import io.mrarm.irc.config.RightClockSettings;
import io.mrarm.irc.util.IRCColorUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class RightClockMessageTextViewTest {

    private Context mContext;

    @Before
    public void setUp() {
        Context app = ApplicationProvider.getApplicationContext();
        mContext = new ContextThemeWrapper(app, R.style.AppTheme);
        RightClockSettings.setEnabled(mContext, false);
    }

    @After
    public void tearDown() {
        RightClockSettings.setEnabled(mContext, false);
    }

    @Test
    public void leftTimestampIsBeforeAvatarAndRemovedFromMessageBody() {
        LinearLayout row = inflateRow();
        TextView left = row.findViewById(R.id.chat_message_time_left);
        ImageView avatar = row.findViewById(R.id.chat_message_avatar);
        RightClockMessageTextView message = row.findViewById(R.id.chat_message);
        TextView right = row.findViewById(R.id.chat_message_time);

        message.setText(message("[06:58.20]", "Nick: hello"));

        assertTrue(row.indexOfChild(left) < row.indexOfChild(avatar));
        assertTrue(row.indexOfChild(avatar) < row.indexOfChild(message));
        assertEquals(View.VISIBLE, left.getVisibility());
        assertEquals(View.GONE, right.getVisibility());
        assertEquals("[06:58.20]", left.getText().toString());
        assertEquals("Nick: hello", message.getText().toString());
    }

    @Test
    public void leftClockUsesOneRenderedSpaceBetweenColumns() {
        LinearLayout row = inflateRow();
        TextView left = row.findViewById(R.id.chat_message_time_left);
        ImageView avatar = row.findViewById(R.id.chat_message_avatar);
        RightClockMessageTextView message = row.findViewById(R.id.chat_message);

        message.setText(message("13:25", "Nick: hello"));

        int expectedSpace = Math.max(1, Math.round(left.getPaint().measureText(" ")));
        assertEquals(expectedSpace, message.getPaddingStart());
        ViewGroup.MarginLayoutParams avatarParams =
                (ViewGroup.MarginLayoutParams) avatar.getLayoutParams();
        assertEquals(expectedSpace, avatarParams.getMarginStart());
    }

    @Test
    public void leftTimestampColumnKeepsEqualWidthAcrossDifferentDigits() {
        LinearLayout row = inflateRow();
        TextView left = row.findViewById(R.id.chat_message_time_left);
        RightClockMessageTextView message = row.findViewById(R.id.chat_message);

        message.setText(message("[11:11.11]", "First"));
        int firstWidth = left.getMinWidth();
        message.setText(message("[88:88.88]", "Second"));
        int secondWidth = left.getMinWidth();

        assertTrue(firstWidth > 0);
        assertEquals(firstWidth, secondWidth);
    }

    @Test
    public void selectableTextMatchesRenderedMessageBody() {
        LinearLayout row = inflateRow();
        RightClockMessageTextView message = row.findViewById(R.id.chat_message);
        CharSequence source = message("10:16", "Guest9243:\nhttps://media.example/image.png");

        message.setText(source);

        assertEquals(message.getText().toString(),
                RightClockMessageTextView.getDisplayedMessageText(mContext, source).toString());
        assertEquals("Guest9243:\nhttps://media.example/image.png",
                RightClockMessageTextView.getDisplayedMessageText(mContext, source).toString());
    }

    @Test
    public void rightClockStillUsesTrailingClockView() {
        LinearLayout row = inflateRow();
        TextView left = row.findViewById(R.id.chat_message_time_left);
        RightClockMessageTextView message = row.findViewById(R.id.chat_message);
        TextView right = row.findViewById(R.id.chat_message_time);
        int originalPaddingStart = message.getPaddingStart();

        RightClockSettings.setEnabled(mContext, true);
        message.setText(message("[06:58.20]", "Nick: hello"));

        assertEquals(View.GONE, left.getVisibility());
        assertEquals(View.VISIBLE, right.getVisibility());
        assertEquals("[06:58.20]", right.getText().toString());
        assertEquals("Nick: hello", message.getText().toString());
        assertEquals(originalPaddingStart, message.getPaddingStart());
    }

    private LinearLayout inflateRow() {
        return (LinearLayout) LayoutInflater.from(mContext)
                .inflate(R.layout.chat_message, null, false);
    }

    private CharSequence message(String timestamp, String body) {
        SpannableStringBuilder text = new SpannableStringBuilder(timestamp + " " + body);
        text.setSpan(new ForegroundColorSpan(IRCColorUtils.getTimestampTextColor(mContext)),
                0, timestamp.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }
}
