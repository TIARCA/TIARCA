package io.mrarm.irc;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.mrarm.irc.config.InterfaceSettingsRefreshState;
import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.util.AppLocaleManager;
import io.mrarm.irc.util.DiagnosticLog;
import io.mrarm.irc.util.theme.AppearancePresetManager;

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
        DiagnosticLog.initialize(this);
        installDiagnosticCrashHandler();
        DiagnosticLog.i(this, "APP", () -> "Application process started");
        clearSessionRestoreAfterIntentionalExit();
        AppLocaleManager.applyStoredLanguage(this);
        migrateDefaultThemeForV18();
        AppearancePresetManager.getInstance(this);
        NotificationManager.createDefaultChannels(this);
        DirectShareManager.initialize(this);
        registerActivityLifecycleCallbacks(this);
    }

    private void installDiagnosticCrashHandler() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            String threadName = thread == null ? "unknown" : thread.getName();
            DiagnosticLog.e(this, "CRASH", () ->
                    "Uncaught exception thread=" + threadName, error);
            if (previous != null)
                previous.uncaughtException(thread, error);
        });
    }

    private void clearSessionRestoreAfterIntentionalExit() {
        SharedPreferences preferences = SettingsHelper.getPreferences();
        if (!preferences.getBoolean(PREF_INTENTIONAL_EXIT, false))
            return;
        DiagnosticLog.d(this, "APP", () ->
                "Clearing session restore state after intentional exit");
        clearConnectedServersFile();
        preferences.edit().remove(PREF_INTENTIONAL_EXIT).apply();
    }

    private void clearConnectedServersFile() {
        File connectedServers = new File(getFilesDir(),
                ServerConnectionManager.CONNECTED_SERVERS_FILE_PATH);
        if (connectedServers.exists()) {
            boolean deleted = connectedServers.delete();
            DiagnosticLog.d(this, "APP", () ->
                    "Connected-server recovery file deleted=" + deleted);
        }
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
        DiagnosticLog.d(this, "APP", () -> "Legacy default theme migration applied");
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
            if (!exitCallback.onAppPreExit()) {
                DiagnosticLog.d(this, "APP", () -> "Explicit app exit vetoed by callback");
                return false;
            }
        }
        DiagnosticLog.i(this, "APP", () -> "Explicit app exit requested");
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
        DiagnosticLog.d(this, "LIFECYCLE", () ->
                activity.getClass().getSimpleName() + " created restored=" +
                        (savedInstanceState != null));
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        DiagnosticLog.d(this, "LIFECYCLE", () ->
                activity.getClass().getSimpleName() + " destroyed changingConfigurations=" +
                        activity.isChangingConfigurations());
        mActivities.remove(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        DiagnosticLog.d(this, "LIFECYCLE", () ->
                activity.getClass().getSimpleName() + " started");
    }

    @Override
    public void onActivityResumed(Activity activity) {
        DiagnosticLog.d(this, "LIFECYCLE", () ->
                activity.getClass().getSimpleName() + " resumed");
        if (activity instanceof MainActivity) {
            // Interface settings are edited in a separate Activity while MainActivity remains
            // paused underneath it. Recreate only after the user has actually left the Interface
            // section and only when a relevant preference changed, so every already-instantiated
            // chat tab is rebound from the persisted settings in one pass.
            if (InterfaceSettingsRefreshState.consumeRefreshPending()) {
                MainActivity mainActivity = (MainActivity) activity;
                // Theme changes already schedule their own recreation in ThemedActivity.onStart().
                if (!mainActivity.hasThemeChanged()) {
                    DiagnosticLog.d(this, "LIFECYCLE", () ->
                            "MainActivity recreating after interface settings change");
                    mainActivity.recreate();
                }
                return;
            }
            UpdateManager.maybePromptAndCheck(activity);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        DiagnosticLog.d(this, "LIFECYCLE", () ->
                activity.getClass().getSimpleName() + " paused");
    }

    @Override
    public void onActivityStopped(Activity activity) {
        DiagnosticLog.d(this, "LIFECYCLE", () ->
                activity.getClass().getSimpleName() + " stopped");
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        DiagnosticLog.d(this, "LIFECYCLE", () ->
                activity.getClass().getSimpleName() + " saving instance state");
    }


    public interface PreExitCallback {
        boolean onAppPreExit();
    }


    public interface ExitCallback {
        void onAppExiting();
    }

}
