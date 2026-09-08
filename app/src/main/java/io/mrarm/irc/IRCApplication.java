package io.mrarm.irc;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.util.AppLocaleManager;

public class IRCApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private static final String PREF_V18_THEME_MIGRATED = "v18_theme_migrated";
    private static final String PREF_INTENTIONAL_EXIT = "intentional_exit";

    private List<Activity> mActivities = new ArrayList<>();
    private List<PreExitCallback> mPreExitCallbacks = new ArrayList<>();
    private List<ExitCallback> mExitCallbacks = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        SettingsHelper.getInstance(this);
        clearSessionRestoreAfterIntentionalExit();
        AppLocaleManager.applyStoredLanguage(this);
        migrateDefaultThemeForV18();
        NotificationManager.createDefaultChannels(this);
        DirectShareManager.initialize(this);
        registerActivityLifecycleCallbacks(this);
    }

    private void clearSessionRestoreAfterIntentionalExit() {
        SharedPreferences preferences = SettingsHelper.getPreferences();
        if (!preferences.getBoolean(PREF_INTENTIONAL_EXIT, false))
            return;
        clearConnectedServersFile();
        preferences.edit().remove(PREF_INTENTIONAL_EXIT).apply();
    }

    private void clearConnectedServersFile() {
        File connectedServers = new File(getFilesDir(),
                ServerConnectionManager.CONNECTED_SERVERS_FILE_PATH);
        if (connectedServers.exists())
            connectedServers.delete();
    }

    private void migrateDefaultThemeForV18() {
        SharedPreferences preferences = SettingsHelper.getPreferences();
        if (preferences.getBoolean(PREF_V18_THEME_MIGRATED, false))
            return;
        String theme = preferences.getString("theme", null);
        SharedPreferences.Editor editor = preferences.edit();
        if (theme == null || "default".equals(theme) || "default_dark".equals(theme))
            editor.putString("theme", "default_dark");
        editor.putBoolean(PREF_V18_THEME_MIGRATED, true).apply();
    }

    public void addPreExitCallback(PreExitCallback c) {
        mPreExitCallbacks.add(c);
    }

    public void removePreExitCallback(PreExitCallback c) {
        mPreExitCallbacks.remove(c);
    }

    public void addExitCallback(ExitCallback c) {
        mExitCallbacks.add(c);
    }

    public void removeExitCallback(ExitCallback c) {
        mExitCallbacks.remove(c);
    }

    public boolean requestExit() {
        for (PreExitCallback exitCallback : mPreExitCallbacks) {
            if (!exitCallback.onAppPreExit())
                return false;
        }
        SettingsHelper.getPreferences().edit().putBoolean(PREF_INTENTIONAL_EXIT, true).commit();
        for (ExitCallback exitCallback : mExitCallbacks)
            exitCallback.onAppExiting();
        for (Activity activity : mActivities)
            activity.finish();
        ServerConnectionManager.destroyInstance();
        IRCService.stop(this);
        // The connected-servers file is crash/session recovery state, not server configuration.
        // Clear it only for an explicit user exit so reopening TIARCA starts a fresh session.
        clearConnectedServersFile();
        return true;
    }


    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        mActivities.add(activity);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        mActivities.remove(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }


    public interface PreExitCallback {
        boolean onAppPreExit();
    }


    public interface ExitCallback {
        void onAppExiting();
    }

}
