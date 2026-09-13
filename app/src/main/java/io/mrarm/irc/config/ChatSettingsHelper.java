package io.mrarm.irc.config;

import android.graphics.Typeface;

import androidx.core.content.res.ResourcesCompat;

import java.io.File;

import io.mrarm.irc.setting.ListWithCustomSetting;
import io.mrarm.irc.R;
import io.mrarm.irc.util.theme.AppearancePresetManager;

public class ChatSettingsHelper {

    private static Typeface sCachedFont;

    public static Typeface getFont() {
        if (sCachedFont != null)
            return sCachedFont;
        String font = ChatSettings.getFontString();
        if (ListWithCustomSetting.isPrefCustomValue(font)) {
            File file = ListWithCustomSetting.getCustomFile(SettingsHelper.getContext(),
                    ChatSettings.PREF_FONT, font);
            try {
                sCachedFont = Typeface.createFromFile(file);
                return sCachedFont;
            } catch (Exception ignored) {
            }
        }
        if (font.equals("monospace"))
            return Typeface.MONOSPACE;
        else if (font.equals("serif"))
            return Typeface.SERIF;
        else if (font.equals(AppearancePresetManager.FONT_ATKINSON_HYPERLEGIBLE_NEXT)) {
            Typeface bundled = ResourcesCompat.getFont(SettingsHelper.getContext(),
                    R.font.atkinson_hyperlegible_next);
            if (bundled != null) {
                sCachedFont = bundled;
                return sCachedFont;
            }
        }
        else
            return Typeface.DEFAULT;
        return Typeface.DEFAULT;
    }

    static {
        SettingsHelper.changeEvent().listen(ChatSettings.PREF_FONT, () -> {
            sCachedFont = null;
        });
    }

}
