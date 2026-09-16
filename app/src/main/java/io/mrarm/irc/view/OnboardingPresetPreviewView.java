package io.mrarm.irc.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.chatlib.dto.NickPrefixList;
import io.mrarm.irc.R;
import io.mrarm.irc.config.ChatSettings;
import io.mrarm.irc.util.AlignToPointSpan;
import io.mrarm.irc.util.IRCColorUtils;
import io.mrarm.irc.util.InitialAvatarDrawable;
import io.mrarm.irc.util.MessageBuilder;
import io.mrarm.irc.util.StyledAttributesHelper;

/**
 * Renders the onboarding appearance preview from the same chat-row layout and message formatter
 * used by real conversations.
 *
 * OnboardingActivity still feeds this view a frame from the old compact sprite. The bitmap itself
 * is intentionally ignored: keeping this interception point makes the change local and reversible
 * while the preview is generated at runtime from deterministic, entirely local demo messages.
 */
public class OnboardingPresetPreviewView extends AppCompatImageView {

    private boolean mRenderRequested;
    private boolean mRendering;
    private int mLastRenderedWidth = -1;

    public OnboardingPresetPreviewView(Context context) {
        super(context);
    }

    public OnboardingPresetPreviewView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public OnboardingPresetPreviewView(Context context, @Nullable AttributeSet attrs,
                                       int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setImageBitmap(Bitmap ignored) {
        mRenderRequested = true;
        post(this::renderPreviewIfReady);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mRenderRequested && w > 0 && w != oldw)
            post(this::renderPreviewIfReady);
    }

    private void renderPreviewIfReady() {
        if (!mRenderRequested || mRendering)
            return;
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        if (width <= 0 || width == mLastRenderedWidth)
            return;

        mRendering = true;
        try {
            LinearLayout conversation = new LinearLayout(getContext());
            conversation.setOrientation(LinearLayout.VERTICAL);
            conversation.setBackgroundColor(StyledAttributesHelper.getColor(getContext(),
                    android.R.attr.colorBackground, Color.TRANSPARENT));
            int verticalPadding = dp(6);
            conversation.setPadding(0, verticalPadding, 0, verticalPadding);

            MessageBuilder builder = new MessageBuilder(getContext());
            Typeface typeface = ChatSettings.getFont();
            int fontSize = ChatSettings.getFontSize();
            for (MessageInfo message : createDemoMessages()) {
                View row = LayoutInflater.from(getContext()).inflate(
                        R.layout.chat_message, conversation, false);
                bindMessageRow(row, message, builder, typeface, fontSize);
                conversation.addView(row);
            }

            int widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            conversation.measure(widthSpec, heightSpec);
            int height = Math.max(1, conversation.getMeasuredHeight());
            conversation.layout(0, 0, width, height);

            Bitmap preview = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            conversation.draw(new Canvas(preview));
            mLastRenderedWidth = width;
            super.setImageBitmap(preview);
        } finally {
            mRendering = false;
        }
    }

    private void bindMessageRow(View row, MessageInfo message, MessageBuilder builder,
                                Typeface typeface, int fontSize) {
        RightClockMessageTextView body = row.findViewById(R.id.chat_message);
        ImageView avatar = row.findViewById(R.id.chat_message_avatar);

        if (typeface != null)
            body.setTypeface(typeface);
        if (fontSize != -1)
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);

        bindAvatar(avatar, message, builder);
        body.setText(AlignToPointSpan.apply(body, builder.buildMessage(message)));
    }

    private void bindAvatar(ImageView avatar, MessageInfo message, MessageBuilder builder) {
        MessageSenderInfo sender = message.getSender();
        String nick = sender == null ? null : sender.getNick();
        boolean userMessage = message.getType() == MessageInfo.MessageType.NORMAL ||
                message.getType() == MessageInfo.MessageType.ME ||
                message.getType() == MessageInfo.MessageType.NOTICE;
        if (!builder.getMessageAvatars() || !userMessage || nick == null || nick.isEmpty()) {
            avatar.setImageDrawable(null);
            avatar.setVisibility(View.GONE);
            return;
        }

        avatar.setImageDrawable(new InitialAvatarDrawable(nick,
                IRCColorUtils.getNickColor(getContext(), nick)));
        avatar.setVisibility(View.VISIBLE);
    }

    private List<MessageInfo> createDemoMessages() {
        List<MessageInfo> messages = new ArrayList<>();

        MessageSenderInfo chasuble = sender("Chasuble", "@");
        MessageSenderInfo missPrism = sender("Miss_Prism", "%");
        MessageSenderInfo algernon = sender("Algernon", "+");
        MessageSenderInfo jack = sender("Jack", "@");
        MessageSenderInfo ladyBracknell = sender("Lady_Bracknell", "@");

        // Public-domain excerpt adapted as an IRC conversation from Oscar Wilde's
        // The Importance of Being Earnest. Timestamps and IRC ranks are demo metadata.
        messages.add(message(chasuble, 22, 47, 8, "Lætitia!", MessageInfo.MessageType.NORMAL));
        messages.add(message(chasuble, 22, 47, 13, "embraces Miss_Prism",
                MessageInfo.MessageType.ME));
        messages.add(message(missPrism, 22, 47, 21, "Frederick! At last! 😄",
                MessageInfo.MessageType.NORMAL));
        messages.add(message(algernon, 22, 47, 34, "Cecily! At last!",
                MessageInfo.MessageType.NORMAL));
        messages.add(message(algernon, 22, 47, 39, "embraces Cecily",
                MessageInfo.MessageType.ME));
        messages.add(message(jack, 22, 48, 2, "Gwendolen! At last!",
                MessageInfo.MessageType.NORMAL));
        messages.add(message(jack, 22, 48, 7, "embraces Gwendolen",
                MessageInfo.MessageType.ME));
        messages.add(message(ladyBracknell, 22, 48, 26,
                "My nephew, you seem to be displaying signs of triviality.",
                MessageInfo.MessageType.NORMAL));
        messages.add(message(jack, 22, 48, 41,
                "On the contrary, aunt Augusta, I’ve now realised for the first time in my life " +
                        "the vital Importance of Being Earnest.",
                MessageInfo.MessageType.NORMAL));
        return messages;
    }

    private MessageSenderInfo sender(String nick, String prefix) {
        return new MessageSenderInfo(nick, null, null, new NickPrefixList(prefix), null);
    }

    private MessageInfo message(MessageSenderInfo sender, int hour, int minute, int second,
                                String text, MessageInfo.MessageType type) {
        return new MessageInfo(sender, demoTime(hour, minute, second), text, type);
    }

    private Date demoTime(int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2026, Calendar.SEPTEMBER, 16, hour, minute, second);
        return calendar.getTime();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
