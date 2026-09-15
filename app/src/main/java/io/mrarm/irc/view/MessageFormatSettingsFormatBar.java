package io.mrarm.irc.view;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import android.text.style.ForegroundColorSpan;

import io.mrarm.irc.dialog.ColorListPickerDialog;
import io.mrarm.irc.R;
import io.mrarm.irc.util.IRCColorUtils;
import io.mrarm.irc.util.MessageBuilder;

public class MessageFormatSettingsFormatBar extends TextFormatBar {

    public MessageFormatSettingsFormatBar(Context context) {
        this(context, null);
    }

    public MessageFormatSettingsFormatBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.textFormatBarStyle);
    }

    public MessageFormatSettingsFormatBar(Context context, @Nullable AttributeSet attrs,
                                          int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        addElementsHint();
    }

    private void addElementsHint() {
        RelativeLayout bar = findViewById(R.id.formatting_bar);
        if (bar == null)
            return;

        AppCompatTextView hint = new AppCompatTextView(getContext());
        hint.setText(R.string.message_format_add_elements_hint);
        hint.setTextColor(ContextCompat.getColor(
                getContext(), R.color.messageFormatEditorControlText));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hint.setGravity(Gravity.CENTER);
        hint.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        hint.setMaxLines(2);
        hint.setIncludeFontPadding(false);
        int horizontalPadding = Math.round(4 * getResources().getDisplayMetrics().density);
        hint.setPadding(horizontalPadding, 0, horizontalPadding, 0);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.START_OF, R.id.format_extra);
        params.addRule(RelativeLayout.LEFT_OF, R.id.format_extra);
        bar.addView(hint, params);
    }

    @Override
    protected ColorListPickerDialog createColorPicker(boolean fillColor, int selectedColor) {
        ColorListPickerDialog ret = super.createColorPicker(fillColor, selectedColor);
        if (!fillColor) {
            ret.setColors(getResources().getIntArray(R.array.formatTextColors), -1);
            ret.setSelectedColor(selectedColor);
            ret.setNeutralButton(R.string.message_format_sender_color,
                    (DialogInterface dialog, int which) -> {
                        removeSpan(ForegroundColorSpan.class);
                        setSpan(new MessageBuilder.MetaForegroundColorSpan(getContext(),
                                MessageBuilder.MetaForegroundColorSpan.COLOR_SENDER));
                    });
            ret.setOnColorChangeListener((ColorListPickerDialog d, int newColorIndex, int color) -> {
                removeSpan(ForegroundColorSpan.class);
                if (color == IRCColorUtils.getStatusTextColor(getContext())) {
                    setSpan(new MessageBuilder.MetaForegroundColorSpan(getContext(), MessageBuilder.MetaForegroundColorSpan.COLOR_STATUS));
                } else if (color == IRCColorUtils.getTimestampTextColor(getContext())) {
                    setSpan(new MessageBuilder.MetaForegroundColorSpan(getContext(), MessageBuilder.MetaForegroundColorSpan.COLOR_TIMESTAMP));
                } else {
                    setSpan(new ForegroundColorSpan(color));
                }
                d.cancel();
            });
        }
        return ret;
    }

}
