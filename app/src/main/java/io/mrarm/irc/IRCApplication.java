package io.mrarm.irc;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.util.AppLocaleManager;

public class IRCApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private static final String PREF_V18_THEME_MIGRATED = "v18_theme_migrated";

    private List<Activity> mActivities = new ArrayList<>();
    private List<PreExitCallback> mPreExitCallbacks = new ArrayList<>();
    private List<ExitCallback> mExitCallbacks = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        SettingsHelper.getInstance(this);
        AppLocaleManager.applyStoredLanguage(this);
        migrateDefaultThemeForV18();
        NotificationManager.createDefaultChannels(this);
        DirectShareManager.initialize(this);
        registerActivityLifecycleCallbacks(this);
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
        for (ExitCallback exitCallback : mExitCallbacks)
            exitCallback.onAppExiting();
        for (Activity activity : mActivities)
            activity.finish();
        ServerConnectionManager.destroyInstance();
        IRCService.stop(this);
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
