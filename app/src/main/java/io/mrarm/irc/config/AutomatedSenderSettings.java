package io.mrarm.irc.config;

/** Visual-only preference for automated IRC identities. */
public final class AutomatedSenderSettings {

    public static final String PREF_MONOCHROME_BOTS = "monochrome_bots";

    private AutomatedSenderSettings() {
    }

    public static Object getDefaultValue(String key) {
        if (PREF_MONOCHROME_BOTS.equals(key))
            return false;
        return null;
    }
}
