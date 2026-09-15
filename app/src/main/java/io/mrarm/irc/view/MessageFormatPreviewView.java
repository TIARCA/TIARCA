package io.mrarm.irc.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import java.util.Date;

import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.irc.R;
import io.mrarm.irc.config.RightClockSettings;
import io.mrarm.irc.util.AlignToPointSpan;
import io.mrarm.irc.util.DefaultPreferences;
import io.mrarm.irc.util.MessageBuilder;

/**
 * WYSIWYG preview for the message-format settings screen.
 *
 * It mirrors the real chat-row order: left clock, optional avatar, message body, optional right
 * clock. The demo avatar is intentionally synthetic so the preview is deterministic.
 */
public class MessageFormatPreviewView extends LinearLayout
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int DEMO_AVATAR_PURPLE = Color.rgb(91, 44, 131);

    private final TextView mLeftClock;
    private final TextView mAvatar;
    private final TextView mBody;
    private final TextView mRightClock;

    private MessageBuilder mBuilder;
    private MessageInfo mMessage;

    public MessageFormatPreviewView(Context context) {
        this(context, null);
    }

    public MessageFormatPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.TOP);
        // Keep the sample visually separated from the controls below it even when the body wraps
        // to a second line. This also prevents the next section title from feeling clipped into the
        // preview on compact screens.
        setMinimumHeight(dp(96));

        mLeftClock = createTextView(context);
        mLeftClock.setTextColor(ContextCompat.getColor(context, R.color.messageTimestamp));
        addView(mLeftClock, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        mAvatar = createTextView(context);
        mAvatar.setText("M");
        mAvatar.setTextColor(Color.WHITE);
        mAvatar.setGravity(Gravity.CENTER);
        mAvatar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        GradientDrawable avatarBackground = new GradientDrawable();
        avatarBackground.setShape(GradientDrawable.OVAL);
        avatarBackground.setColor(DEMO_AVATAR_PURPLE);
        ViewCompat.setBackground(mAvatar, avatarBackground);
        int avatarSize = dp(32);
        addView(mAvatar, new LayoutParams(avatarSize, avatarSize));

        mBody = createTextView(context);
        LayoutParams bodyParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        addView(mBody, bodyParams);

        mRightClock = createTextView(context);
        mRightClock.setTextColor(ContextCompat.getColor(context, R.color.messageTimestamp));
        addView(mRightClock, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    private AppCompatTextView createTextView(Context context) {
        AppCompatTextView view = new AppCompatTextView(context);
        view.setTextAppearance(context, R.style.TextAppearance_AppCompat_Body1);
        return view;
    }

    public void setPreview(MessageBuilder builder, MessageInfo message) {
        mBuilder = builder;
        mMessage = message;
        render();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        DefaultPreferences.get(getContext()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        DefaultPreferences.get(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (RightClockSettings.PREF_MESSAGE_TIME_RIGHT.equals(key))
            render();
    }

    private void render() {
        if (mBuilder == null || mMessage == null)
            return;

        CharSequence rendered = mBuilder.buildMessage(mMessage);
        Date date = mMessage.getDate();
        String timestamp = date == null ? "" : mBuilder.getMessageTimeFormat().format(date);
        SpannableStringBuilder body = new SpannableStringBuilder(rendered);
        int timestampStart = body.toString().indexOf(timestamp);
        boolean hasTimestamp = !timestamp.isEmpty() && timestampStart >= 0;
        if (hasTimestamp) {
            body.delete(timestampStart, timestampStart + timestamp.length());
            if (timestampStart < body.length() && body.charAt(timestampStart) == ' ')
                body.delete(timestampStart, timestampStart + 1);
            else if (timestampStart > 0 && body.charAt(timestampStart - 1) == ' ')
                body.delete(timestampStart - 1, timestampStart);
        }

        if (body instanceof Spannable)
            AlignToPointSpan.apply(mBody, body);
        mBody.setText(new SpannableString(body));

        boolean rightClock = RightClockSettings.isEnabled(getContext());
        mLeftClock.setText(timestamp);
        mRightClock.setText(timestamp);
        mLeftClock.setVisibility(hasTimestamp && !rightClock ? View.VISIBLE : View.GONE);
        mRightClock.setVisibility(hasTimestamp && rightClock ? View.VISIBLE : View.GONE);

        boolean showAvatar = mBuilder.getMessageAvatars();
        mAvatar.setVisibility(showAvatar ? View.VISIBLE : View.GONE);

        int gap = Math.max(1, Math.round(mBody.getPaint().measureText(" ")));
        setStartMargin(mAvatar, hasTimestamp && !rightClock && showAvatar ? gap : 0);
        setStartMargin(mBody, (showAvatar || (hasTimestamp && !rightClock)) ? gap : 0);
        setStartMargin(mRightClock, hasTimestamp && rightClock ? gap : 0);
    }

    private void setStartMargin(View view, int margin) {
        LayoutParams params = (LayoutParams) view.getLayoutParams();
        if (params.getMarginStart() == margin)
            return;
        params.setMarginStart(margin);
        view.setLayoutParams(params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
