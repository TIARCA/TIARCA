package io.mrarm.irc.onboarding;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import io.mrarm.irc.config.AppSettings;
import io.mrarm.irc.config.IdentitySettings;
import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.config.ServerConfigManager;
import io.mrarm.irc.util.DefaultPreferences;

/** First-run gate kept separate from appearance-preset migration state. */
public final class OnboardingState {

    public static final String PREF_COMPLETED = "onboarding_completed_v1";
    public static final UUID SIMOSNAP_UUID = UUID.nameUUIDFromBytes(
            "bundled-server:simosnap".getBytes(StandardCharsets.UTF_8));

    private OnboardingState() {
    }

    public static boolean shouldAutoLaunch(Context context) {
        SharedPreferences prefs = DefaultPreferences.get(context);
        if (prefs.getBoolean(PREF_COMPLETED, false))
            return false;
        if (!isFreshInstall(context))
            return false;
        return !hasMeaningfulUserConfiguration(context);
    }

    public static void markCompleted(Context context) {
        DefaultPreferences.get(context).edit().putBoolean(PREF_COMPLETED, true).apply();
    }

    public static boolean isCompleted(Context context) {
        return DefaultPreferences.get(context).getBoolean(PREF_COMPLETED, false);
    }

    static boolean isFreshInstall(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.firstInstallTime == info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * A restored/configured installation must remain authoritative even when Android reports the
     * package itself as freshly installed. The bundled empty Simosnap starter is intentionally not
     * considered meaningful configuration.
     */
    static boolean hasMeaningfulUserConfiguration(Context context) {
        if (IdentitySettings.hasNickname(AppSettings.getDefaultNicks()))
            return true;

        List<ServerConfigData> servers = ServerConfigManager.getInstance(context).getServers();
        for (ServerConfigData server : servers) {
            if (!SIMOSNAP_UUID.equals(server.uuid))
                return true;
            if (IdentitySettings.hasConfiguredNickname(server))
                return true;
            if (hasText(server.user) || hasText(server.realname) || hasText(server.authMode)
                    || hasText(server.authUser) || hasText(server.authPass) || hasText(server.pass))
                return true;
        }
        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
