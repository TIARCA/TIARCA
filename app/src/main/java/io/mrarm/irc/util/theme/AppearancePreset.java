package io.mrarm.irc.util.theme;

import io.mrarm.irc.R;

/** Stable identities for the complete, user-selectable chat appearance presets. */
public enum AppearancePreset {

    IRC_LIGHT("irc_light", R.string.appearance_preset_irc_light, true),
    IRC_DARK("irc_dark", R.string.appearance_preset_irc_dark, true),
    GRAPHIC_LIGHT("graphic_light", R.string.appearance_preset_graphic_light, true),
    GRAPHIC_DARK("graphic_dark", R.string.appearance_preset_graphic_dark, true),
    TERMINAL("terminal", R.string.appearance_preset_terminal, true),
    COLOR_BLIND("color_blind", R.string.appearance_preset_color_blind, true),
    CUSTOM("custom", R.string.appearance_preset_custom, false);

    private final String id;
    private final int nameResId;
    private final boolean applicable;

    AppearancePreset(String id, int nameResId, boolean applicable) {
        this.id = id;
        this.nameResId = nameResId;
        this.applicable = applicable;
    }

    public String getId() {
        return id;
    }

    public int getNameResId() {
        return nameResId;
    }

    public boolean isApplicable() {
        return applicable;
    }

    public static AppearancePreset fromId(String id) {
        if (id != null) {
            for (AppearancePreset preset : values()) {
                if (preset.id.equals(id))
                    return preset;
            }
        }
        return CUSTOM;
    }
}
