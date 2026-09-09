package io.mrarm.irc.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatCheckBox;

import io.mrarm.irc.config.RightClockSettings;

/** CheckBox that directly persists the right-side clock preference. */
public class RightClockSettingsCheckBox extends AppCompatCheckBox {

    private boolean mInitializing;

    public RightClockSettingsCheckBox(Context context) {
        super(context);
        init();
    }

    public RightClockSettingsCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RightClockSettingsCheckBox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mInitializing = true;
        setChecked(RightClockSettings.isEnabled(getContext()));
        mInitializing = false;
        setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!mInitializing)
                RightClockSettings.setEnabled(getContext(), isChecked);
        });
    }
}
