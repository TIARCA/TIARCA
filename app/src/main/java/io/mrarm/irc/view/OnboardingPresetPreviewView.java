package io.mrarm.irc.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import io.mrarm.irc.R;
import io.mrarm.irc.util.theme.AppearancePreset;
import io.mrarm.irc.util.theme.AppearancePresetManager;

/**
 * Uses one full-resolution real-device crop per built-in appearance preset.
 *
 * OnboardingActivity historically feeds this view a frame cut from the legacy compact sprite.
 * Intercept that call here so the wizard can keep its existing flow while rendering the matching
 * high-resolution resource instead of upscaling the old low-resolution frame.
 */
public class OnboardingPresetPreviewView extends AppCompatImageView {

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
    public void setImageBitmap(Bitmap bitmap) {
        int resource = previewResource(
                AppearancePresetManager.getInstance(getContext()).getCurrentPreset());
        if (resource != 0) {
            super.setImageResource(resource);
            return;
        }
        super.setImageBitmap(bitmap);
    }

    private static int previewResource(AppearancePreset preset) {
        if (preset == null)
            return 0;
        switch (preset) {
            case GRAPHIC_LIGHT:
                return R.drawable.onboarding_preset_graphic_light;
            case GRAPHIC_DARK:
                return R.drawable.onboarding_preset_graphic_dark;
            case IRC_LIGHT:
                return R.drawable.onboarding_preset_irc_light;
            case IRC_DARK:
                return R.drawable.onboarding_preset_irc_dark;
            case TERMINAL:
                return R.drawable.onboarding_preset_terminal;
            case COLOR_BLIND:
                return R.drawable.onboarding_preset_color_blind;
            default:
                return 0;
        }
    }
}
